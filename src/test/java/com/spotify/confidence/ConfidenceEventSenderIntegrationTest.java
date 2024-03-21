package com.spotify.confidence;

import static com.spotify.confidence.EventSenderTestUtils.getFlushPolicies;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.ImmutableMap;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

public class ConfidenceEventSenderIntegrationTest {
  final EventSenderEngine engine =
      new EventSenderEngineImpl(getFlushPolicies(10000000, 20), new FakeUploader());
  private final ResolverClientTestUtils.FakeFlagResolverClient fakeFlagResolverClient =
      new ResolverClientTestUtils.FakeFlagResolverClient();
  final Confidence confidence = new Confidence(null, engine, fakeFlagResolverClient);

  @Test
  public void testStress() throws IOException, InterruptedException {
    int numberOfThreads = 15; // Adjust as needed

    for (int i = 0; i < numberOfThreads; i++) {
      Thread thread = new Thread(new MyTask());
      thread.start();
    }
    Thread.sleep(60000);
    confidence.close(); // Should trigger the upload of an additional incomplete batch
  }

  class MyTask implements Runnable {
    @Override
    public void run() {
      // The code you want to execute on each thread goes here
      while (true) {
        try {
          Thread.sleep(10);
        } catch (InterruptedException e) {
          throw new RuntimeException(e);
        }
        confidence.send("");
      }
    }
  }

  @Test
  public void testEngineUploads() throws IOException {
    final FakeUploader alwaysSucceedUploader = new FakeUploader(List.of());
    final int batchSize = 6;
    final int numEvents = 14;
    final EventSenderEngine engine =
        new EventSenderEngineImpl(getFlushPolicies(10000, batchSize), alwaysSucceedUploader);
    final Confidence confidence = new Confidence(null, engine, fakeFlagResolverClient);
    int size = 0;
    while (size++ < numEvents) {
      confidence.send(
          "navigate", ConfidenceValue.of(ImmutableMap.of("key", ConfidenceValue.of("size"))));
    }

    confidence.close(); // Should trigger the upload of an additional incomplete batch
    final int additionalBatch = (numEvents % batchSize) > 0 ? 1 : 0;

    assertThat(alwaysSucceedUploader.uploadCalls.size())
        .isEqualTo((numEvents / batchSize + additionalBatch));
    final List<EventBatch> fullEventBatches =
        alwaysSucceedUploader.uploadCalls.subList(0, alwaysSucceedUploader.uploadCalls.size() - 1);
    assertThat(fullEventBatches.stream().allMatch(batch -> batch.events().size() == batchSize))
        .isTrue();
    if (additionalBatch != 0) {
      final EventBatch lastBatch =
          alwaysSucceedUploader.uploadCalls.get(alwaysSucceedUploader.uploadCalls.size() - 1);
      assertThat(lastBatch.events().size()).isEqualTo(numEvents % batchSize);
    }
  }

  @Test
  public void testEngineCloseSuccessfullyWithoutEventsQueued() throws IOException {
    final FakeUploader alwaysSucceedUploader = new FakeUploader(List.of());
    final int batchSize = 6;
    final EventSenderEngine engine =
        new EventSenderEngineImpl(getFlushPolicies(10000, batchSize), alwaysSucceedUploader);
    final Confidence confidence = new Confidence(null, engine, fakeFlagResolverClient);

    confidence.close(); // Should trigger the upload of an additional incomplete batch
    assertThat(alwaysSucceedUploader.uploadCalls.size()).isEqualTo(0);
  }

  @Test
  public void testEngineUploadsWhenIntermittentErrorWillRetry() throws IOException {
    final int batchSize = 3;
    final int numEvents = 14;
    // This will fail at the 2nd and 5th upload
    final List<Integer> failAtUploadWithIndex = List.of(2, 5);
    final FakeUploader fakeUploader = new FakeUploader(failAtUploadWithIndex);
    final EventSenderEngine engine =
        new EventSenderEngineImpl(getFlushPolicies(10000, batchSize), fakeUploader);
    final Confidence confidence = new Confidence(null, engine, fakeFlagResolverClient);
    int size = 0;
    while (size++ < numEvents) {
      confidence.send(
          "navigate",
          ConfidenceValue.of(ImmutableMap.of("key", ConfidenceValue.of("size=" + size))));
    }

    confidence.close(); // Should trigger the upload of an additional incomplete batch
    final int additionalBatch = (numEvents % batchSize) > 0 ? 1 : 0;

    // Verify we had the correct number of calls to the uploader (including retries)
    assertThat(fakeUploader.uploadCalls.size())
        .isEqualTo((numEvents / batchSize + additionalBatch) + failAtUploadWithIndex.size());
    // Verify we had the correct number of unique calls to the uploader
    assertThat(Set.copyOf(fakeUploader.uploadCalls).size())
        .isEqualTo((numEvents / batchSize + additionalBatch));
  }

  @Test
  public void multiThreadTest() throws IOException {
    final int numberOfThreads = 50;
    final int numberOfEvents = 100000;
    final int eventsPerThread = numberOfEvents / numberOfThreads;
    final int batchSize = 30;

    final FakeUploader alwaysSucceedUploader = new FakeUploader();
    final EventSenderEngine engine =
        new EventSenderEngineImpl(getFlushPolicies(10000, batchSize), alwaysSucceedUploader);
    final Confidence confidence = new Confidence(null, engine, fakeFlagResolverClient);
    final List<Future<Boolean>> futures = new ArrayList<>();
    final ExecutorService executorService = Executors.newFixedThreadPool(numberOfThreads);

    for (int i = 0; i < numberOfThreads; i++) {
      final Future<Boolean> future =
          executorService.submit(
              () -> {
                for (int j = 0; j < eventsPerThread; j++) {
                  confidence.send(
                      "navigate",
                      ConfidenceValue.of(ImmutableMap.of("key", ConfidenceValue.of("size"))));
                }
              },
              true);
      futures.add(future);
    }
    futures.forEach(
        future -> {
          try {
            future.get();
          } catch (Exception e) {
            throw new RuntimeException(e);
          }
        });
    confidence.close();
    final int additionalBatch = (numberOfEvents % batchSize) > 0 ? 1 : 0;
    final int expectedNumberOfBatches = (numberOfEvents / batchSize) + additionalBatch;
    assertThat(alwaysSucceedUploader.uploadCalls.size()).isEqualTo(expectedNumberOfBatches);
  }
}
