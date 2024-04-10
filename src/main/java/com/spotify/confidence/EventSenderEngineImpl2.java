package com.spotify.confidence;

import dev.failsafe.Failsafe;
import dev.failsafe.FailsafeExecutor;
import dev.failsafe.RetryPolicy;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class EventSenderEngineImpl2 implements EventSenderEngine {

  static final String EVENT_NAME_PREFIX = "eventDefinitions/";
  private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
  private final EventUploader eventUploader;
  private final Clock clock;
  //  private final Supplier<Instant> timeSupplier;
  private final int maxBatchSize;
  private final Duration maxFlushInterval;
  private final FailsafeExecutor<Boolean> uploadExecutor;
  private volatile boolean isClosing = false;

  private final ConcurrentLinkedQueue<Event> sendQueue = new ConcurrentLinkedQueue<>();

  private final AtomicInteger pendingEventCount = new AtomicInteger();
  private final Set<CompletableFuture<?>> pendingBatches = ConcurrentHashMap.newKeySet();
  private volatile Instant latestFlushTime = Instant.now();

  public EventSenderEngineImpl2(
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
                    .build())
//            .with(scheduler)
    ;
    if (maxFlushInterval != 0) {
      schedulePeriodicFlush(this.maxFlushInterval);
    }
  }

  @Override
  public void send(
      String name, ConfidenceValue.Struct context, Optional<ConfidenceValue.Struct> message) {
    if (isClosing) return;
    sendQueue.add(
        new Event(
            EVENT_NAME_PREFIX + name,
            message.orElse(ConfidenceValue.Struct.EMPTY),
            context,
            clock.currentTimeSeconds()));
    if (pendingEventCount.incrementAndGet() % maxBatchSize == 0) {
      flush();
    }
  }

  private void schedulePeriodicFlush(Duration delay) {
    if (isClosing) return;
    final Instant prevFlushTime = latestFlushTime;
    scheduler.schedule(
        () -> {
          // if there hasn't been a flush since we scheduled
          if (prevFlushTime == latestFlushTime) {
            flush();
          }
          final Duration durationSinceLastFlush = Duration.between(latestFlushTime, Instant.now());
          schedulePeriodicFlush(maxFlushInterval.minus(durationSinceLastFlush));
        },
        delay.toMillis(),
        TimeUnit.MILLISECONDS);
  }

  private CompletableFuture<?> flush() {
    latestFlushTime = Instant.now();
    final ArrayList<Event> events = new ArrayList<>();
    for (int i = 0; i < maxBatchSize; i++) {
      Event event = sendQueue.poll();
      if (event == null) break;
      events.add(event);
    }
    if (events.isEmpty()) return CompletableFuture.completedFuture(true);
    final CompletableFuture<Boolean> future =
        uploadExecutor.getStageAsync(() -> eventUploader.upload(new EventBatch(events)));
    pendingBatches.add(future);
    pendingEventCount.addAndGet(-events.size());
    future.whenComplete(
        (res, err) -> {
          // TODO log errors
          pendingBatches.remove(future);
        });
    return future;
  }

  private void awaitPending() {
    CompletableFuture<?>[] pending =
        pendingBatches.stream()
            .map(future -> future.exceptionally(throwable -> null))
            .toArray(CompletableFuture[]::new);
    System.out.println("Waiting for " + pending.length);
    try {
      CompletableFuture.allOf(pending).get(10, TimeUnit.SECONDS);
    } catch (ExecutionException | InterruptedException | TimeoutException e) {
      System.out.println("failed waiting");
    }
    System.out.println("Remaining batches: " + pendingBatches.size());
  }

  @Override
  public synchronized void close() throws IOException {
    if (isClosing) return;
    isClosing = true;
    scheduler.shutdownNow();
    while (!sendQueue.isEmpty()) {
      flush();
    }
    awaitPending();
    pendingBatches.forEach(
        batch -> {
          System.out.println("Cancel batch");
          batch.cancel(true);
        });
    eventUploader.close();
  }
}
