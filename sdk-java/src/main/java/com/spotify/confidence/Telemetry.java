package com.spotify.confidence;

import com.google.common.annotations.VisibleForTesting;
import com.spotify.telemetry.v1.LibraryTraces;
import com.spotify.telemetry.v1.Monitoring;
import com.spotify.telemetry.v1.Platform;
import java.util.concurrent.ConcurrentLinkedQueue;

public class Telemetry {
  private final ConcurrentLinkedQueue<LibraryTraces.Trace> latencyTraces =
      new ConcurrentLinkedQueue<>();
  private final boolean isProvider;

  public Telemetry() {
    this.isProvider = false;
  }

  public Telemetry(boolean isProvider) {
    this.isProvider = isProvider;
  }

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

  @VisibleForTesting
  public Monitoring getSnapshotInternal() {
    final LibraryTraces libraryTraces =
        LibraryTraces.newBuilder()
            .setLibrary(
                isProvider
                    ? LibraryTraces.Library.LIBRARY_OPEN_FEATURE
                    : LibraryTraces.Library.LIBRARY_CONFIDENCE)
            .setLibraryVersion(ConfidenceUtils.getSdkVersion())
            .addAllTraces(latencyTraces)
            .build();

    return Monitoring.newBuilder()
        .setPlatform(Platform.PLATFORM_JAVA)
        .addLibraryTraces(libraryTraces)
        .build();
  }

  private void clear() {
    latencyTraces.clear();
  }

  public boolean isProvider() {
    return isProvider;
  }
}
