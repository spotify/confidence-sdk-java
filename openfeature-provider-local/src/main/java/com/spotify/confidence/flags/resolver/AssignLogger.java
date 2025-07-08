package com.spotify.confidence.flags.resolver;

import static com.google.protobuf.CodedOutputStream.computeMessageSize;

import com.codahale.metrics.Gauge;
import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import com.google.common.annotations.VisibleForTesting;
import com.spotify.confidence.flags.resolver.domain.AccountClient;
import com.spotify.confidence.flags.resolver.domain.FlagToApply;
import com.spotify.confidence.shaded.flags.resolver.v1.InternalFlagLoggerServiceGrpc;
import com.spotify.confidence.shaded.flags.resolver.v1.Sdk;
import com.spotify.confidence.shaded.flags.resolver.v1.TelemetryData;
import com.spotify.confidence.shaded.flags.resolver.v1.WriteFlagAssignedRequest;
import com.spotify.confidence.shaded.flags.resolver.v1.WriteFlagAssignedResponse;
import com.spotify.confidence.shaded.flags.resolver.v1.events.FlagAssigned;
import java.io.Closeable;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AssignLogger implements Closeable {
  private static final Logger logger = LoggerFactory.getLogger(AssignLogger.class);

  private static final Logger LOG = LoggerFactory.getLogger(AssignLogger.class);
  // Max size minus some wiggle room
  private static final int GRPC_MESSAGE_MAX_SIZE = 4194304 - 1048576;

  private final ConcurrentLinkedQueue<FlagAssigned> queue = new ConcurrentLinkedQueue<>();
  private final LongAdder dropCount = new LongAdder();
  private final AtomicLong capacity;
  private Instant lastFlagAssigned = Instant.now();
  private final Timer timer;

  private final InternalFlagLoggerServiceGrpc.InternalFlagLoggerServiceBlockingStub flagLoggerStub;
  private final Meter assigned;

  @VisibleForTesting
  AssignLogger(
      InternalFlagLoggerServiceGrpc.InternalFlagLoggerServiceBlockingStub flagLoggerStub,
      Timer timer,
      MetricRegistry metricRegistry,
      long capacity) {
    this.timer = timer;
    this.flagLoggerStub = flagLoggerStub;
    this.capacity = new AtomicLong(capacity);
    this.assigned = metricRegistry.meter("assign-logger.applies");
    metricRegistry.register(
        "assign-logger.ms-since-last-sync", (Gauge<Long>) this::timeSinceLastAssigned);
    metricRegistry.register(
        "assign-logger.occupancy_ratio",
        (Gauge<Double>) () -> 1.0 - (double) remainingCapacity() / capacity);
  }

  private Long timeSinceLastAssigned() {
    return Duration.between(lastFlagAssigned, Instant.now()).toMillis();
  }

  public static AssignLogger createStarted(
      InternalFlagLoggerServiceGrpc.InternalFlagLoggerServiceBlockingStub flagLoggerStub,
      Duration checkpointInterval,
      MetricRegistry metricRegistry,
      long capacity) {
    final Timer timer = new Timer("assign-logger-timer", true);
    final AssignLogger assignLogger =
        new AssignLogger(flagLoggerStub, timer, metricRegistry, capacity);

    timer.scheduleAtFixedRate(
        new TimerTask() {
          @Override
          public void run() {
            try {
              assignLogger.checkpoint();
            } catch (Exception e) {
              LOG.error("Failed to checkpoint assignments", e);
            }
          }
        },
        checkpointInterval.toMillis(),
        checkpointInterval.toMillis());
    return assignLogger;
  }

  @VisibleForTesting
  synchronized void checkpoint() {
    WriteFlagAssignedRequest.Builder batch = prepareNewBatch();
    int batchSize = batch.build().getSerializedSize();
    FlagAssigned assigned = queue.peek();
    while (assigned != null) {
      final int size =
          computeMessageSize(WriteFlagAssignedRequest.FLAG_ASSIGNED_FIELD_NUMBER, assigned);
      if (batchSize + size > GRPC_MESSAGE_MAX_SIZE) {
        sendBatch(batch.build());
        batch = prepareNewBatch();
        batchSize = batch.build().getSerializedSize();
      }
      batch.addFlagAssigned(queue.poll());
      batchSize += size;
      assigned = queue.peek();
    }
    if (!batch.getFlagAssignedList().isEmpty()) {
      sendBatch(batch.build());
    }
  }

  private WriteFlagAssignedRequest.Builder prepareNewBatch() {
    return WriteFlagAssignedRequest.newBuilder()
        .setTelemetryData(
            TelemetryData.newBuilder().setDroppedEvents(dropCount.sumThenReset()).build());
  }

  private void sendBatch(WriteFlagAssignedRequest batch) {
    try {
      final WriteFlagAssignedResponse response = flagLoggerStub.writeFlagAssigned(batch);
      // return the capacity on successful send
      capacity.getAndAdd(
          batch.getFlagAssignedList().stream().mapToLong(FlagAssigned::getSerializedSize).sum());
      this.assigned.mark(response.getAssignedFlags());
      lastFlagAssigned = Instant.now();
    } catch (RuntimeException ex) {
      logger.error(
          "Could not send assigns, putting {} back on the queue",
          batch.getFlagAssignedList().size(),
          ex);
      // we still own the capacity so can add back directly to queue
      queue.addAll(batch.getFlagAssignedList());
      dropCount.add(batch.getTelemetryData().getDroppedEvents());
      throw ex;
    }
  }

  @VisibleForTesting
  long remainingCapacity() {
    return capacity.get();
  }

  @VisibleForTesting
  long dropCount() {
    return dropCount.sum();
  }

  public void logAssigns(
      String resolveId, Sdk sdk, List<FlagToApply> flagsToApply, AccountClient accountClient) {
    logAssigns(FlagLogger.createFlagAssigned(resolveId, sdk, flagsToApply, accountClient));
  }

  public void logAssigns(FlagAssigned assigned) {
    final int size = assigned.getSerializedSize();
    for (long c = capacity.get(); size <= c; c = capacity.get()) {
      if (capacity.compareAndSet(c, c - size)) {
        queue.add(assigned);
        return;
      }
    }
    dropCount.increment();
  }

  @Override
  public void close() {
    timer.cancel();
    checkpoint();
  }
}
