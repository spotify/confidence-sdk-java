package com.spotify.confidence;

import static com.spotify.confidence.EventSenderEngineImpl.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableMap;
import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import java.io.IOException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

public class EventSenderEngineTest {

  private final ResolverClientTestUtils.FakeFlagResolverClient fakeFlagResolverClient =
      new ResolverClientTestUtils.FakeFlagResolverClient();

  private final FakeClock clock = new FakeClock();

  @Test
  public void testEngineRejectsEventsAfterClosed() throws IOException {
    final FakeUploader alwaysSucceedUploader = new FakeUploader(List.of());
    final int maxBatchSize = 6;
    final EventSenderEngine engine =
        new EventSenderEngineImpl(
            maxBatchSize,
            alwaysSucceedUploader,
            clock,
            DEFAULT_MAX_FLUSH_INTERVAL,
            DEFAULT_MAX_MEMORY_CONSUMPTION);
    engine.send(
        "navigate",
        ConfidenceValue.of(ImmutableMap.of("key", ConfidenceValue.of("size"))),
        Optional.empty());
    engine.close();
    engine.send(
        "navigate",
        ConfidenceValue.of(ImmutableMap.of("key", ConfidenceValue.of("size"))),
        Optional.empty());
    assertThat(alwaysSucceedUploader.uploadCalls.size()).isEqualTo(1);
  }

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
    int size = 0;
    while (size++ < numEvents) {
      engine.send(
          "navigate",
          ConfidenceValue.of(ImmutableMap.of("key", ConfidenceValue.of("size"))),
          Optional.empty());
    }

    engine.close(); // Should trigger the upload of an additional incomplete batch
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
  public void testOverlappingKeysInPayload() throws InterruptedException {
    final FakeUploader alwaysSucceedUploader = new FakeUploader(List.of());
    final EventSenderEngine engine =
        new EventSenderEngineImpl(
            1,
            alwaysSucceedUploader,
            clock,
            DEFAULT_MAX_FLUSH_INTERVAL,
            DEFAULT_MAX_MEMORY_CONSUMPTION);
    // wait for the flush timeout to trigger the upload
    engine.send(
        "my_event",
        ConfidenceValue.of(
            ImmutableMap.of(
                "a", ConfidenceValue.of(2),
                "message", ConfidenceValue.of(3))),
        Optional.of(
            ConfidenceValue.Struct.of(
                Map.of(
                    "a", ConfidenceValue.of(0),
                    "message", ConfidenceValue.of(1)))));
    Thread.sleep(300);
    assertThat(alwaysSucceedUploader.uploadCalls.peek().get(0).getPayload())
        .isEqualTo(
            Struct.newBuilder()
                .putAllFields(
                    Map.of(
                        "a",
                        Value.newBuilder().setNumberValue(0).build(),
                        "message",
                        Value.newBuilder().setNumberValue(1).build()))
                .build());
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

    engine.close(); // Should trigger the upload of an additional incomplete batch
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

    // send only one event
    engine.send(
        "navigate",
        ConfidenceValue.of(ImmutableMap.of("key", ConfidenceValue.of("size"))),
        Optional.empty());

    // wait for the flush timeout to trigger the upload
    Thread.sleep(300);
    // assert
    assertThat(alwaysSucceedUploader.uploadCalls.size()).isEqualTo(1);
    assertThat(alwaysSucceedUploader.uploadCalls.peek().size()).isEqualTo(1);

    // close
    engine.close();
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
    for (int i = 0; i < numEvents; i++) {
      engine.send(
          "test",
          ConfidenceValue.Struct.EMPTY,
          Optional.of(ConfidenceValue.of(ImmutableMap.of("id", ConfidenceValue.of(i)))));
    }

    engine.close(); // Should trigger the upload of an additional incomplete batch
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
    final CompletableFuture<?>[] eventTasks = new CompletableFuture[numberOfEvents];

    final Stopwatch timer = Stopwatch.createStarted();
    for (int i = 0; i < numberOfEvents; i++) {
      // run all tasks on the common pool
      eventTasks[i] =
          CompletableFuture.runAsync(
              () -> {
                engine.send(
                    "navigate",
                    ConfidenceValue.of(ImmutableMap.of("key", ConfidenceValue.of("size"))),
                    Optional.empty());
              });
    }
    CompletableFuture.allOf(eventTasks).join();
    engine.close();
    System.out.println("Finished in (ms): " + timer.elapsed(TimeUnit.MILLISECONDS));
    final int uploadedEventCount =
        alwaysSucceedUploader.uploadCalls.stream().mapToInt(batch -> batch.size()).sum();
    assertThat(uploadedEventCount).isEqualTo(numberOfEvents);

    final int additionalBatch = (numberOfEvents % maxBatchSize) > 0 ? 1 : 0;
    final int expectedNumberOfBatches = (numberOfEvents / maxBatchSize) + additionalBatch;
    assertThat(alwaysSucceedUploader.uploadCalls.size()).isEqualTo(expectedNumberOfBatches);
  }

  @Test
  public void testUnsentEventsAreCancelledOnThreadInterrupted() throws Exception {
    final CompletableFuture<Boolean> batchResult = new CompletableFuture<>();
    final CompletableFuture<Void> isUploadCalled = new CompletableFuture<>();
    final EventUploader fakeUploader =
        events -> {
          isUploadCalled.complete(null);
          return batchResult;
        };

    // set up the engine so that it cannot support more than 1 event in memory
    final EventSenderEngineImpl engine =
        new EventSenderEngineImpl(10, fakeUploader, clock, Duration.ofMillis(10), 1024);
    engine.send("fake", ConfidenceValue.Struct.EMPTY, Optional.empty());
    isUploadCalled.join();
    Thread.currentThread().interrupt();
    engine.close();
    assertThat(batchResult.isCancelled()).isTrue();
  }

  @Test
  public void testEngineWillRejectEventsIfOverMemoryThreshold() throws IOException {
    final var expectedEvent =
        EventUploader.event("navigate", ConfidenceValue.Struct.EMPTY, Optional.empty())
            .setEventTime(clock.getTimestamp())
            .build();

    final FakeUploader fakeUploader = new FakeUploader();

    // set up the engine so that it cannot support more than 1 event in memory
    final EventSenderEngineImpl engine =
        new EventSenderEngineImpl(
            10, fakeUploader, clock, DEFAULT_MAX_FLUSH_INTERVAL, expectedEvent.getSerializedSize());

    // send two events
    engine.send("navigate", ConfidenceValue.of(Map.of()), Optional.empty());
    assertThat(engine.getEstimatedMemoryConsumption()).isEqualTo(expectedEvent.getSerializedSize());
    engine.send("navigate", ConfidenceValue.of(Map.of()), Optional.empty());

    engine.close();
    // the first event should be uploaded but the second one should not because it was rejected and
    // never added to the queue
    assertThat(fakeUploader.uploadCalls.size()).isEqualTo(1);
  }

  @Test
  public void testEngineThrowsExceptionWhenMaxFlushIntervalIsZero() {
    assertThatThrownBy(
            () ->
                new EventSenderEngineImpl(
                    10, new FakeUploader(), clock, Duration.ZERO, DEFAULT_MAX_MEMORY_CONSUMPTION))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("maxFlushInterval must be positive");
  }
}
