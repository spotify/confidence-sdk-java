package com.spotify.confidence;

import com.google.common.annotations.VisibleForTesting;
import com.spotify.confidence.shaded.flags.resolver.v1.ResolveReason;
import com.spotify.telemetry.v1.LibraryTraces;
import com.spotify.telemetry.v1.Monitoring;
import com.spotify.telemetry.v1.Platform;
import java.util.concurrent.ConcurrentLinkedQueue;
import javax.annotation.Nullable;

public class Telemetry {
  private final ConcurrentLinkedQueue<LibraryTraces.Trace> traces = new ConcurrentLinkedQueue<>();
  private final boolean isProvider;

  public Telemetry() {
    this.isProvider = false;
  }

  public Telemetry(boolean isProvider) {
    this.isProvider = isProvider;
  }

  public void appendLatency(long latency) {
    traces.add(
        LibraryTraces.Trace.newBuilder()
            .setId(LibraryTraces.TraceId.TRACE_ID_RESOLVE_LATENCY)
            .setRequestTrace(
                LibraryTraces.Trace.RequestTrace.newBuilder()
                    .setMillisecondDuration(latency)
                    .setStatus(LibraryTraces.Trace.RequestTrace.Status.STATUS_SUCCESS)
                    .build())
            .build());
  }

  public void appendEvaluation(
      LibraryTraces.Trace.EvaluationTrace.EvaluationReason reason,
      LibraryTraces.Trace.EvaluationTrace.EvaluationErrorCode errorCode) {
    traces.add(
        LibraryTraces.Trace.newBuilder()
            .setId(LibraryTraces.TraceId.TRACE_ID_FLAG_EVALUATION)
            .setEvaluationTrace(
                LibraryTraces.Trace.EvaluationTrace.newBuilder()
                    .setReason(reason)
                    .setErrorCode(errorCode)
                    .build())
            .build());
  }

  public static LibraryTraces.Trace.EvaluationTrace.EvaluationReason mapReason(
      ResolveReason resolveReason, @Nullable ErrorType errorType) {
    if (errorType != null) {
      return LibraryTraces.Trace.EvaluationTrace.EvaluationReason.EVALUATION_REASON_ERROR;
    }
    switch (resolveReason) {
      case RESOLVE_REASON_MATCH:
        return LibraryTraces.Trace.EvaluationTrace.EvaluationReason
            .EVALUATION_REASON_TARGETING_MATCH;
      case RESOLVE_REASON_NO_SEGMENT_MATCH:
      case RESOLVE_REASON_NO_TREATMENT_MATCH:
        return LibraryTraces.Trace.EvaluationTrace.EvaluationReason.EVALUATION_REASON_DEFAULT;
      case RESOLVE_REASON_FLAG_ARCHIVED:
        return LibraryTraces.Trace.EvaluationTrace.EvaluationReason.EVALUATION_REASON_DISABLED;
      case RESOLVE_REASON_TARGETING_KEY_ERROR:
      case RESOLVE_REASON_ERROR:
        return LibraryTraces.Trace.EvaluationTrace.EvaluationReason.EVALUATION_REASON_ERROR;
      default:
        return LibraryTraces.Trace.EvaluationTrace.EvaluationReason.EVALUATION_REASON_UNSPECIFIED;
    }
  }

  public static LibraryTraces.Trace.EvaluationTrace.EvaluationErrorCode mapErrorCode(
      ResolveReason resolveReason, @Nullable ErrorType errorType) {
    if (errorType != null) {
      switch (errorType) {
        case FLAG_NOT_FOUND:
          return LibraryTraces.Trace.EvaluationTrace.EvaluationErrorCode
              .EVALUATION_ERROR_CODE_FLAG_NOT_FOUND;
        case INVALID_VALUE_TYPE:
          return LibraryTraces.Trace.EvaluationTrace.EvaluationErrorCode
              .EVALUATION_ERROR_CODE_TYPE_MISMATCH;
        case INVALID_CONTEXT:
          return LibraryTraces.Trace.EvaluationTrace.EvaluationErrorCode
              .EVALUATION_ERROR_CODE_INVALID_CONTEXT;
        case PARSE_ERROR:
          return LibraryTraces.Trace.EvaluationTrace.EvaluationErrorCode
              .EVALUATION_ERROR_CODE_PARSE_ERROR;
        case PROVIDER_NOT_READY:
          return LibraryTraces.Trace.EvaluationTrace.EvaluationErrorCode
              .EVALUATION_ERROR_CODE_PROVIDER_NOT_READY;
        default:
          return LibraryTraces.Trace.EvaluationTrace.EvaluationErrorCode
              .EVALUATION_ERROR_CODE_GENERAL;
      }
    }
    switch (resolveReason) {
      case RESOLVE_REASON_TARGETING_KEY_ERROR:
        return LibraryTraces.Trace.EvaluationTrace.EvaluationErrorCode
            .EVALUATION_ERROR_CODE_TARGETING_KEY_MISSING;
      case RESOLVE_REASON_ERROR:
        return LibraryTraces.Trace.EvaluationTrace.EvaluationErrorCode
            .EVALUATION_ERROR_CODE_GENERAL;
      default:
        return LibraryTraces.Trace.EvaluationTrace.EvaluationErrorCode
            .EVALUATION_ERROR_CODE_UNSPECIFIED;
    }
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
            .addAllTraces(traces)
            .build();

    return Monitoring.newBuilder()
        .setPlatform(Platform.PLATFORM_JAVA)
        .addLibraryTraces(libraryTraces)
        .build();
  }

  private void clear() {
    traces.clear();
  }

  public boolean isProvider() {
    return isProvider;
  }
}
