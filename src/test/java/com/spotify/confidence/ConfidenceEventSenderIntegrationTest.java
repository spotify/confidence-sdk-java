package com.spotify.confidence;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableMap;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

public class ConfidenceEventSenderIntegrationTest {

  private final ResolverClientTestUtils.FakeFlagResolverClient fakeFlagResolverClient =
      new ResolverClientTestUtils.FakeFlagResolverClient();

  private final FakeClock clock = new FakeClock();

  @Test
  public void testEngineUploads() throws IOException {
    final FakeUploader alwaysSucceedUploader = new FakeUploader(List.of());
    final int maxBatchSize = 6;
    final int numEvents = 14;
    final EventSenderEngine engine =
        new EventSenderEngineImpl2(maxBatchSize, alwaysSucceedUploader, clock, 0);
    final Confidence confidence = Confidence.create(engine, fakeFlagResolverClient);
    int size = 0;
    while (size++ < numEvents) {
      confidence.send(
          "navigate", ConfidenceValue.of(ImmutableMap.of("key", ConfidenceValue.of("size"))));
    }

    confidence.close(); // Should trigger the upload of an additional incomplete batch
    final int additionalBatch = (numEvents % maxBatchSize) > 0 ? 1 : 0;

    final int uploadCallsCount = alwaysSucceedUploader.uploadCalls.size();
    final int fullBatchCount =
        (int)
            alwaysSucceedUploader.uploadCalls.stream()
                .filter(batch -> batch.events().size() == maxBatchSize)
                .count();
    final int eventsCount =
        alwaysSucceedUploader.uploadCalls.stream().mapToInt(batch -> batch.events().size()).sum();

    assertThat(uploadCallsCount).isEqualTo((numEvents / maxBatchSize + additionalBatch));
    assertThat(eventsCount).isEqualTo(numEvents);
    assertThat(uploadCallsCount - fullBatchCount).isEqualTo(additionalBatch);
  }

  @Test
  public void testEngineCloseSuccessfullyWithoutEventsQueued() throws IOException {
    final FakeUploader alwaysSucceedUploader = new FakeUploader(List.of());
    final int maxBatchSize = 6;
    final EventSenderEngine engine =
        new EventSenderEngineImpl2(maxBatchSize, alwaysSucceedUploader, clock, 500);
    final Confidence confidence = Confidence.create(engine, fakeFlagResolverClient);

    confidence.close(); // Should trigger the upload of an additional incomplete batch
    assertThat(alwaysSucceedUploader.uploadCalls.size()).isEqualTo(0);
  }

  @Test
  public void testEngineUploadsTriggeredByFlushTimeout() throws IOException, InterruptedException {
    final FakeUploader alwaysSucceedUploader = new FakeUploader(List.of());
    final int maxBatchSize = 6;
    final EventSenderEngine engine =
        new EventSenderEngineImpl2(maxBatchSize, alwaysSucceedUploader, clock, 100);
    final Confidence confidence = Confidence.create(engine, fakeFlagResolverClient);

    // send only one event
    confidence.send(
        "navigate", ConfidenceValue.of(ImmutableMap.of("key", ConfidenceValue.of("size"))));

    // wait for the flush timeout to trigger the upload
    Thread.sleep(300);
    // assert
    assertThat(alwaysSucceedUploader.uploadCalls.size()).isEqualTo(1);
    assertThat(alwaysSucceedUploader.uploadCalls.peek().events().size()).isEqualTo(1);

    // close
    confidence.close();
  }

  @Test
  public void testEngineUploadsWhenIntermittentErrorWillRetry() throws IOException {
    final int maxBatchSize = 3;
    final int numEvents = 14;
    // This will fail at the 2nd and 5th upload
    final List<Integer> failAtUploadWithIndex = List.of(2, 5);
    final FakeUploader fakeUploader = new FakeUploader(failAtUploadWithIndex);
    final EventSenderEngine engine =
        new EventSenderEngineImpl2(maxBatchSize, fakeUploader, clock, 0);
    final Confidence confidence = Confidence.create(engine, fakeFlagResolverClient);
    for (int i = 0; i < numEvents; i++) {
      confidence.send(
          "test",
          ConfidenceValue.of(ImmutableMap.of("id", ConfidenceValue.of(i))));
    }

    confidence.close(); // Should trigger the upload of an additional incomplete batch
    final int additionalBatch = (numEvents % maxBatchSize) > 0 ? 1 : 0;

    // Verify we had the correct number of calls to the uploader (including retries)
    assertThat(fakeUploader.uploadCalls.size())
        .isEqualTo((numEvents / maxBatchSize + additionalBatch) + failAtUploadWithIndex.size());

    Set<ConfidenceValue> uniqueEventIds = fakeUploader.uploadCalls.stream().flatMap(batch -> batch.events().stream()).map(event -> event.message().get("id")).collect(Collectors.toSet());
    // Verify all events reached the uploader
    assertThat(uniqueEventIds.size())
        .isEqualTo(numEvents);
  }

  @Test
  public void multiThreadTest() throws IOException {
    final int numberOfEvents = 100000;
    final int maxBatchSize = 30;

    final FakeUploader alwaysSucceedUploader = new FakeUploader();
    final EventSenderEngine engine =
        new EventSenderEngineImpl2(maxBatchSize, alwaysSucceedUploader, clock, 0);
    final Confidence confidence = Confidence.create(engine, fakeFlagResolverClient);
    final CompletableFuture<?>[] eventTasks = new CompletableFuture[numberOfEvents];

    Stopwatch timer = Stopwatch.createStarted();
    for (int i = 0; i < numberOfEvents; i++) {
      // run all tasks on the common pool
      eventTasks[i] = CompletableFuture.runAsync(() -> {
        confidence.send(
                "navigate",
                ConfidenceValue.of(ImmutableMap.of("key", ConfidenceValue.of("size"))));
      });
    }
    CompletableFuture.allOf(eventTasks).join();
    confidence.close();
    System.out.println("Finished in (ms): " + timer.elapsed(TimeUnit.MILLISECONDS));
    final int uploadedEventCount =
        alwaysSucceedUploader.uploadCalls.stream().mapToInt(batch -> batch.events().size()).sum();
    assertThat(uploadedEventCount).isEqualTo(numberOfEvents);

    final int additionalBatch = (numberOfEvents % maxBatchSize) > 0 ? 1 : 0;
    final int expectedNumberOfBatches = (numberOfEvents / maxBatchSize) + additionalBatch;
    assertThat(alwaysSucceedUploader.uploadCalls.size()).isEqualTo(expectedNumberOfBatches);
  }
}
