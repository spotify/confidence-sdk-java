package com.spotify.confidence;

import dev.failsafe.Failsafe;
import dev.failsafe.FailsafeExecutor;
import dev.failsafe.RetryPolicy;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.LockSupport;

public class EventSenderEngineImpl implements EventSenderEngine {

  static final String EVENT_NAME_PREFIX = "eventDefinitions/";
  private final EventUploader eventUploader;
  private final Clock clock;
  //  private final Supplier<Instant> timeSupplier;
  private final int maxBatchSize;
  private final Duration maxFlushInterval;
  private final FailsafeExecutor<Boolean> uploadExecutor;
  private final ConcurrentLinkedQueue<Event> sendQueue = new ConcurrentLinkedQueue<>();
  private final Set<CompletableFuture<?>> pendingBatches = ConcurrentHashMap.newKeySet();
  private final Thread pollingThread = new Thread(this::pollLoop);
  private volatile boolean intakeClosed = false;

  public EventSenderEngineImpl(
      int maxBatchSize, EventUploader eventUploader, Clock clock, int maxFlushInterval) {
    this.eventUploader = eventUploader;
    this.clock = clock;
    this.maxBatchSize = maxBatchSize;
    this.maxFlushInterval = Duration.ofMillis(maxFlushInterval);

    uploadExecutor =
        Failsafe.with(
            RetryPolicy.<Boolean>builder()
                .handleResult(false)
                .withBackoff(1, 10, ChronoUnit.SECONDS)
                .withJitter(0.1)
                .withMaxAttempts(-1)
                .withMaxDuration(Duration.ofMinutes(30))
                .build());
    pollingThread.start();
  }

  @Override
  public void send(
      String name, ConfidenceValue.Struct context, Optional<ConfidenceValue.Struct> message) {
    if (intakeClosed) return;
    sendQueue.add(
        new Event(
            EVENT_NAME_PREFIX + name,
            message.orElse(ConfidenceValue.Struct.EMPTY),
            context,
            clock.currentTimeSeconds()));
    LockSupport.unpark(pollingThread);
  }

  public void pollLoop() {
    Instant latestFlushTime = Instant.now();
    ArrayList<Event> events = new ArrayList<>();
    while (true) {

      Event event = sendQueue.poll();
      if (event != null) {
        events.add(event);
      } else {
        if (intakeClosed) break;
        LockSupport.parkUntil(Instant.now().plus(maxFlushInterval).toEpochMilli());
      }
      boolean passedMaxFlushInterval =
          !maxFlushInterval.isZero()
              && Duration.between(latestFlushTime, Instant.now()).compareTo(maxFlushInterval) > 0;
      if (events.size() == maxBatchSize || passedMaxFlushInterval) {
        upload(events);
        events = new ArrayList<>();
        latestFlushTime = Instant.now();
      }
    }
    upload(events);
  }

  private void upload(List<Event> events) {
    if (events.isEmpty()) return;
    final CompletableFuture<Boolean> batchUploaded =
        uploadExecutor.getStageAsync(() -> eventUploader.upload(new EventBatch(events)));
    pendingBatches.add(batchUploaded);
    batchUploaded.whenComplete(
        (res, err) -> {
          // TODO log errors
          pendingBatches.remove(batchUploaded);
        });
  }

  private void awaitPending() {
    try {
      LockSupport.unpark(pollingThread);
      pollingThread.join();
      CompletableFuture<?>[] pending =
          pendingBatches.stream()
              .map(future -> future.exceptionally(throwable -> null))
              .toArray(CompletableFuture[]::new);
      CompletableFuture.allOf(pending).get(10, TimeUnit.SECONDS);
    } catch (ExecutionException | InterruptedException | TimeoutException e) {
    }
  }

  @Override
  public synchronized void close() throws IOException {
    if (intakeClosed) return;
    intakeClosed = true;
    awaitPending();
    pendingBatches.forEach(
        batch -> {
          batch.cancel(true);
        });
    eventUploader.close();
  }
}
