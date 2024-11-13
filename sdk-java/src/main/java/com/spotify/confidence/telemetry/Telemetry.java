package com.spotify.confidence.telemetry;

import com.google.common.annotations.VisibleForTesting;
import com.spotify.telemetry.v1.LibraryTraces;
import com.spotify.telemetry.v1.Monitoring;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

public class Telemetry {
  private final ConcurrentLinkedQueue<LibraryTraces.Trace> latencyTraces =
      new ConcurrentLinkedQueue<>();
  private final AtomicBoolean isProvider = new AtomicBoolean(false);

  public void appendLatency(long latency) {
    latencyTraces.add(
        LibraryTraces.Trace.newBuilder()
            .setId(LibraryTraces.TraceId.TRACE_ID_RESOLVE_LATENCY)
            .setMillisecondDuration(latency)
            .build());
  }

  public Monitoring getSnapshot() {
    final Monitoring snapshot = getSnapshotInternal();
    clear();
    return snapshot;
  }

  private Monitoring getSnapshotInternal() {
    final LibraryTraces libraryTraces =
        LibraryTraces.newBuilder()
            .setLibrary(
                isProvider.get()
                    ? LibraryTraces.Library.LIBRARY_OPEN_FEATURE
                    : LibraryTraces.Library.LIBRARY_CONFIDENCE)
            .addAllTraces(latencyTraces)
            .build();

    return Monitoring.newBuilder().addLibraryTraces(libraryTraces).build();
  }

  @VisibleForTesting
  public synchronized Monitoring peekSnapshot() {
    return getSnapshotInternal();
  }

  private void clear() {
    latencyTraces.clear();
  }

  public void setIsProvider(Boolean isProvider) {
    this.isProvider.set(isProvider);
  }
}
