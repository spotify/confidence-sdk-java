package com.spotify.confidence;

import static com.spotify.confidence.EventSenderEngineImpl.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableMap;
import com.google.protobuf.Timestamp;
import com.google.protobuf.Value;
import java.io.IOException;
import java.time.Duration;
import java.util.*;
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
        new EventSenderEngineImpl(
            maxBatchSize,
            alwaysSucceedUploader,
            clock,
            DEFAULT_MAX_FLUSH_INTERVAL,
            DEFAULT_MAX_MEMORY_CONSUMPTION);
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
                .filter(batch -> batch.size() == maxBatchSize)
                .count();
    final int eventsCount =
        alwaysSucceedUploader.uploadCalls.stream().mapToInt(batch -> batch.size()).sum();

    assertThat(uploadCallsCount).isEqualTo((numEvents / maxBatchSize + additionalBatch));
    assertThat(eventsCount).isEqualTo(numEvents);
    assertThat(uploadCallsCount - fullBatchCount).isEqualTo(additionalBatch);
  }

  @Test
  public void testEngineCloseSuccessfullyWithoutEventsQueued() throws IOException {
    final FakeUploader alwaysSucceedUploader = new FakeUploader(List.of());
    final int maxBatchSize = 6;
    final EventSenderEngine engine =
        new EventSenderEngineImpl(
            maxBatchSize,
            alwaysSucceedUploader,
            clock,
            DEFAULT_MAX_FLUSH_INTERVAL,
            DEFAULT_MAX_MEMORY_CONSUMPTION);
    final Confidence confidence = Confidence.create(engine, fakeFlagResolverClient);

    confidence.close(); // Should trigger the upload of an additional incomplete batch
    assertThat(alwaysSucceedUploader.uploadCalls.size()).isEqualTo(0);
  }

  @Test
  public void testEngineUploadsTriggeredByFlushTimeout() throws IOException, InterruptedException {
    final FakeUploader alwaysSucceedUploader = new FakeUploader(List.of());
    final int maxBatchSize = 6;
    final EventSenderEngine engine =
        new EventSenderEngineImpl(
            maxBatchSize,
            alwaysSucceedUploader,
            clock,
            Duration.ofMillis(100),
            DEFAULT_MAX_MEMORY_CONSUMPTION);
    final Confidence confidence = Confidence.create(engine, fakeFlagResolverClient);

    // send only one event
    confidence.send(
        "navigate", ConfidenceValue.of(ImmutableMap.of("key", ConfidenceValue.of("size"))));

    // wait for the flush timeout to trigger the upload
    Thread.sleep(300);
    // assert
    assertThat(alwaysSucceedUploader.uploadCalls.size()).isEqualTo(1);
    assertThat(alwaysSucceedUploader.uploadCalls.peek().size()).isEqualTo(1);

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
        new EventSenderEngineImpl(
            maxBatchSize,
            fakeUploader,
            clock,
            DEFAULT_MAX_FLUSH_INTERVAL,
            DEFAULT_MAX_MEMORY_CONSUMPTION);
    final Confidence confidence = Confidence.create(engine, fakeFlagResolverClient);
    for (int i = 0; i < numEvents; i++) {
      confidence.send("test", ConfidenceValue.of(ImmutableMap.of("id", ConfidenceValue.of(i))));
    }

    confidence.close(); // Should trigger the upload of an additional incomplete batch
    final int additionalBatch = (numEvents % maxBatchSize) > 0 ? 1 : 0;

    // Verify we had the correct number of calls to the uploader (including retries)
    assertThat(fakeUploader.uploadCalls.size())
        .isEqualTo((numEvents / maxBatchSize + additionalBatch) + failAtUploadWithIndex.size());

    final Set<Value> uniqueEventIds =
        fakeUploader.uploadCalls.stream()
            .flatMap(Collection::stream)
            .map(event -> event.getPayload().getFieldsMap().get("id"))
            .collect(Collectors.toSet());
    // Verify all events reached the uploader
    assertThat(uniqueEventIds.size()).isEqualTo(numEvents);
  }

  @Test
  public void multiThreadTest() throws IOException {
    final int numberOfEvents = 100000;
    final int maxBatchSize = 30;

    final FakeUploader alwaysSucceedUploader = new FakeUploader();
    final EventSenderEngine engine =
        new EventSenderEngineImpl(
            maxBatchSize,
            alwaysSucceedUploader,
            clock,
            DEFAULT_MAX_FLUSH_INTERVAL,
            DEFAULT_MAX_MEMORY_CONSUMPTION);
    final Confidence confidence = Confidence.create(engine, fakeFlagResolverClient);
    final CompletableFuture<?>[] eventTasks = new CompletableFuture[numberOfEvents];

    final Stopwatch timer = Stopwatch.createStarted();
    for (int i = 0; i < numberOfEvents; i++) {
      // run all tasks on the common pool
      eventTasks[i] =
          CompletableFuture.runAsync(
              () -> {
                confidence.send(
                    "navigate",
                    ConfidenceValue.of(ImmutableMap.of("key", ConfidenceValue.of("size"))));
              });
    }
    CompletableFuture.allOf(eventTasks).join();
    confidence.close();
    System.out.println("Finished in (ms): " + timer.elapsed(TimeUnit.MILLISECONDS));
    final int uploadedEventCount =
        alwaysSucceedUploader.uploadCalls.stream().mapToInt(batch -> batch.size()).sum();
    assertThat(uploadedEventCount).isEqualTo(numberOfEvents);

    final int additionalBatch = (numberOfEvents % maxBatchSize) > 0 ? 1 : 0;
    final int expectedNumberOfBatches = (numberOfEvents / maxBatchSize) + additionalBatch;
    assertThat(alwaysSucceedUploader.uploadCalls.size()).isEqualTo(expectedNumberOfBatches);
  }

  @Test
  public void testEngineWillRejectEventsIfOverMemoryThreshold() throws IOException {
    final var expectedEvent =
        event("navigate", ConfidenceValue.Struct.EMPTY, Optional.empty())
            .setEventTime(Timestamp.newBuilder().setSeconds(clock.currentTimeSeconds()))
            .build();

    final FakeUploader fakeUploader = new FakeUploader();

    // set up the engine so that it cannot support more than 1 event in memory
    final EventSenderEngineImpl engine =
        new EventSenderEngineImpl(
            10, fakeUploader, clock, DEFAULT_MAX_FLUSH_INTERVAL, expectedEvent.getSerializedSize());
    final Confidence confidence = Confidence.create(engine, fakeFlagResolverClient);

    // send two events
    confidence.send("navigate", ConfidenceValue.of(Map.of()));
    assertThat(engine.getEstimatedMemoryConsumption()).isEqualTo(expectedEvent.getSerializedSize());
    confidence.send("navigate", ConfidenceValue.of(Map.of()));

    confidence.close();
    assertThat(engine.getEstimatedMemoryConsumption()).isEqualTo(0);
    // the first event should be uploaded but the second one should not because it was rejected and
    // never added to the queue
    assertThat(fakeUploader.uploadCalls.size()).isEqualTo(1);
  }
}
