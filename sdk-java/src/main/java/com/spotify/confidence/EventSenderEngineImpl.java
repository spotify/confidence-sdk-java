package com.spotify.confidence;

import com.google.common.annotations.VisibleForTesting;
import com.spotify.confidence.events.v1.Event;
import dev.failsafe.Failsafe;
import dev.failsafe.FailsafeExecutor;
import dev.failsafe.RetryPolicy;
import io.grpc.ManagedChannel;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;
import org.slf4j.Logger;

class EventSenderEngineImpl implements EventSenderEngine {

  static final String EVENT_NAME_PREFIX = "eventDefinitions/";
  static final int DEFAULT_BATCH_SIZE = 25;
  static final Duration DEFAULT_MAX_FLUSH_INTERVAL = Duration.ofSeconds(60);
  static final long DEFAULT_MAX_MEMORY_CONSUMPTION = 1024 * 1024 * 1024; // 1GB
  private static final Logger log = org.slf4j.LoggerFactory.getLogger(EventSenderEngineImpl.class);
  private final EventUploader eventUploader;
  private final Clock clock;
  private final int maxBatchSize;
  private final Duration maxFlushInterval;
  private final FailsafeExecutor<Boolean> uploadExecutor;
  private final ConcurrentLinkedQueue<com.spotify.confidence.events.v1.Event> sendQueue =
      new ConcurrentLinkedQueue<>();
  private final Set<CompletableFuture<?>> pendingBatches = ConcurrentHashMap.newKeySet();
  private final Thread pollingThread = new Thread(this::pollLoop);
  private final long maxMemoryConsumption;
  private volatile boolean intakeClosed = false;
  private volatile boolean joinWasInterrupted = false;
  private final AtomicLong estimatedMemoryConsumption = new AtomicLong(0);

  @VisibleForTesting
  EventSenderEngineImpl(
      int maxBatchSize,
      EventUploader eventUploader,
      Clock clock,
      Duration maxFlushInterval,
      long maxMemoryConsumption) {
    if (maxFlushInterval.isZero()) {
      throw new IllegalArgumentException("maxFlushInterval must be positive");
    }
    this.eventUploader = eventUploader;
    this.clock = clock;
    this.maxBatchSize = maxBatchSize;
    this.maxFlushInterval = maxFlushInterval;
    this.maxMemoryConsumption = maxMemoryConsumption;
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

  EventSenderEngineImpl(
      String clientSecret, ManagedChannel channel, Clock clock, int deadlineMillis) {
    this(
        DEFAULT_BATCH_SIZE,
        new GrpcEventUploader(clientSecret, clock, channel, deadlineMillis),
        clock,
        DEFAULT_MAX_FLUSH_INTERVAL,
        DEFAULT_MAX_MEMORY_CONSUMPTION);
  }

  @Override
  public void emit(
      String name, ConfidenceValue.Struct context, Optional<ConfidenceValue.Struct> data) {
    if (intakeClosed) {
      log.warn("EventSenderEngine is closed, dropping event {}", name);
      return;
    }
    final Event event =
        EventUploader.event(name, context, data).setEventTime(clock.getTimestamp()).build();
    if (estimatedMemoryConsumption.get() + event.getSerializedSize() > maxMemoryConsumption) {
      log.warn("EventSenderEngine is overloaded, dropping event {}", name);
      return;
    }
    sendQueue.add(event);
    estimatedMemoryConsumption.addAndGet(event.getSerializedSize());
    LockSupport.unpark(pollingThread);
  }

  @Override
  public void flush() {
    sendQueue.add(Event.newBuilder().setEventDefinition("manual_flash").build());
    LockSupport.unpark(pollingThread);
  }

  private void pollLoop() {
    Instant latestFlushTime = Instant.now();
    ArrayList<com.spotify.confidence.events.v1.Event> events = new ArrayList<>();
    while (true) {
      final var event = sendQueue.poll();
      if (event != null) {
        if ("manual_flash".equals(event.getEventDefinition())) {
          log.debug("Starting events upload due to manual flush");
          upload(events);
          events = new ArrayList<>();
          continue;
        } else {
          events.add(event);
        }
      } else {
        if (intakeClosed) break;
        LockSupport.parkUntil(Instant.now().plus(maxFlushInterval).toEpochMilli());
      }
      final boolean passedMaxFlushInterval =
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

  private void upload(List<com.spotify.confidence.events.v1.Event> events) {
    if (events.isEmpty()) return;
    final CompletableFuture<Boolean> batchUploaded =
        uploadExecutor.getStageAsync(
            () -> {
              // we don't want to upload if the thread was interrupted on close
              if (joinWasInterrupted) {
                return CompletableFuture.completedFuture(true);
              }
              return eventUploader.upload(events);
            });
    pendingBatches.add(batchUploaded);
    batchUploaded.whenComplete(
        (res, err) -> {
          // Errors from this is logged by the uploader
          pendingBatches.remove(batchUploaded);
          estimatedMemoryConsumption.addAndGet(
              -events.stream().mapToLong(Event::getSerializedSize).sum());
        });
  }

  private void joinPollingThread() {
    try {
      LockSupport.unpark(pollingThread);
      pollingThread.join();
    } catch (InterruptedException e) {
      sendQueue.clear();
      joinWasInterrupted = true;
      Thread.currentThread().interrupt();
    }
  }

  private void awaitPending() {
    try {
      final CompletableFuture<?>[] pending =
          pendingBatches.stream()
              .map(future -> future.exceptionally(throwable -> null))
              .toArray(CompletableFuture[]::new);
      CompletableFuture.allOf(pending).get(10, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      // reset the interrupt status
      Thread.currentThread().interrupt();
    } catch (ExecutionException | TimeoutException ignored) {
    }
  }

  @VisibleForTesting
  long getEstimatedMemoryConsumption() {
    return estimatedMemoryConsumption.get();
  }

  @Override
  public synchronized void close() throws IOException {
    if (intakeClosed) return;
    intakeClosed = true;
    joinPollingThread();
    awaitPending();

    pendingBatches.forEach(
        batch -> {
          batch.cancel(true);
        });
  }
}
