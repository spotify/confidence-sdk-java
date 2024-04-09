package com.spotify.confidence;

import dev.failsafe.Failsafe;
import dev.failsafe.FailsafeExecutor;
import dev.failsafe.RetryPolicy;
import java.io.IOException;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Optional;
import java.util.concurrent.*;

public class EventSenderEngineImpl2 implements EventSenderEngine {

  static final String EVENT_NAME_PREFIX = "eventDefinitions/";
  private final ScheduledExecutorService executorService = Executors.newScheduledThreadPool(1);
  private final EventUploader eventUploader;
  private final Clock clock;
  private final int maxBatchSize;
  private final int flushTimeoutMilliseconds;
  private final FailsafeExecutor<Boolean> uploadExecutor;
  private ScheduledFuture<?> pendingFlush;
  private volatile boolean isClosing = false;

  private final ConcurrentLinkedQueue<Event> sendQueue = new ConcurrentLinkedQueue<>();

  public EventSenderEngineImpl2(
      int maxBatchSize, EventUploader eventUploader, Clock clock, int flushTimeoutMilliseconds) {
    this.eventUploader = eventUploader;
    this.clock = clock;
    this.maxBatchSize = maxBatchSize;
    this.flushTimeoutMilliseconds = flushTimeoutMilliseconds;

    uploadExecutor = Failsafe.with(RetryPolicy.<Boolean>builder()
            .handleResult(false)
            .withBackoff(1, 10, ChronoUnit.SECONDS)
            .withJitter(0.1)
            .withMaxAttempts(-1)
            .withMaxDuration(Duration.ofMinutes(30))
            .build());
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
    if (sendQueue.size() >= maxBatchSize) {
      flush();
    } else {
      scheduleFlush();
    }
  }

  private void cancelPendingFlush() {
    // Cancel the existing scheduled task if it exists
    if (pendingFlush != null && !pendingFlush.isDone()) {
      pendingFlush.cancel(false);
    }
    pendingFlush = null;
  }

  private void scheduleFlush() {
    cancelPendingFlush();
    if (flushTimeoutMilliseconds > 0) {
      pendingFlush =
          executorService.schedule(this::flush, flushTimeoutMilliseconds, TimeUnit.MILLISECONDS);
    }
  }

  private synchronized void flush() {
    cancelPendingFlush();
    // ta max-batch-size events fr[n sendQueue
    final ArrayList<Event> events = new ArrayList<>();
    for (int i = 0; i < maxBatchSize; i++) {
      Event event = sendQueue.poll();
      if (event == null) break;
      events.add(event);
    }
    uploadExecutor.getStageAsync(() -> eventUploader.upload(new EventBatch(events)));
  }

  @Override
  public synchronized void close() throws IOException {
    if (isClosing) return;
    isClosing = true;
    while (!sendQueue.isEmpty()) {
      flush();
    }
  }
}
