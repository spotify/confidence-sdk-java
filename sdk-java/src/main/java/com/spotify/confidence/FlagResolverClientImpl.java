package com.spotify.confidence;

import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import com.spotify.confidence.shaded.flags.resolver.v1.ResolveFlagsResponse;
import com.spotify.confidence.telemetry.Telemetry;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nullable;

class FlagResolverClientImpl implements FlagResolverClient {
  public static final String OPEN_FEATURE_RESOLVE_CONTEXT_KEY = "open-feature";
  private final FlagResolver grpcFlagResolver;
  private final Telemetry telemetry;

  public FlagResolverClientImpl(FlagResolver grpcFlagResolver, @Nullable Telemetry telemetry) {
    this.grpcFlagResolver = grpcFlagResolver;
    this.telemetry = telemetry;
  }

  public FlagResolverClientImpl(FlagResolver grpcFlagResolver) {
    this.grpcFlagResolver = grpcFlagResolver;
    this.telemetry = null;
  }

  public CompletableFuture<ResolveFlagsResponse> resolveFlags(
      String flagName, ConfidenceValue.Struct context, Boolean isProvider) {
    final Instant start = Instant.now();

    final Struct.Builder evaluationContextBuilder = context.toProto().getStructValue().toBuilder();
    if (context.asMap().containsKey(OPEN_FEATURE_RESOLVE_CONTEXT_KEY)) {
      final Value openFeatureEvaluationContext =
          context.asMap().get(OPEN_FEATURE_RESOLVE_CONTEXT_KEY).toProto();

      evaluationContextBuilder.putAllFields(
          openFeatureEvaluationContext.getStructValue().getFieldsMap());
      evaluationContextBuilder.removeFields(OPEN_FEATURE_RESOLVE_CONTEXT_KEY);
    }
    if (telemetry != null) {
      telemetry.setIsProvider(isProvider);
    }

    return this.grpcFlagResolver
        .resolve(flagName, evaluationContextBuilder.build(), isProvider)
        .thenApply(
            response -> {
              if (telemetry != null) {
                final Instant end = Instant.now();
                telemetry.appendLatency(Duration.between(start, end).toMillis());
              }
              return response;
            });
  }

  @Override
  public void close() {
    this.grpcFlagResolver.close();
  }
}
