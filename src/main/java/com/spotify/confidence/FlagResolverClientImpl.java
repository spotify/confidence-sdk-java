package com.spotify.confidence;

import static com.spotify.confidence.ConfidenceFeatureProvider.OPEN_FEATURE_RESOLVE_CONTEXT_KEY;

import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import com.spotify.confidence.shaded.flags.resolver.v1.ResolveFlagsResponse;
import java.util.concurrent.CompletableFuture;

class FlagResolverClientImpl implements FlagResolverClient {
  private final FlagResolver grpcFlagResolver;

  public FlagResolverClientImpl(FlagResolver grpcFlagResolver) {
    this.grpcFlagResolver = grpcFlagResolver;
  }

  public CompletableFuture<ResolveFlagsResponse> resolveFlags(
      String flagName, ConfidenceValue.Struct context, Boolean isProvider) {
    final Struct.Builder evaluationContextBuilder = context.toProto().getStructValue().toBuilder();
    if (context.asMap().containsKey(OPEN_FEATURE_RESOLVE_CONTEXT_KEY)) {
      final Value openFeatureEvaluationContext =
          context.asMap().get(OPEN_FEATURE_RESOLVE_CONTEXT_KEY).toProto();

      evaluationContextBuilder.putAllFields(
          openFeatureEvaluationContext.getStructValue().getFieldsMap());
      evaluationContextBuilder.removeFields(OPEN_FEATURE_RESOLVE_CONTEXT_KEY);
    }

    return this.grpcFlagResolver.resolve(flagName, evaluationContextBuilder.build(), isProvider);
  }

  @Override
  public void close() {
    this.grpcFlagResolver.close();
  }
}
