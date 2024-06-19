package com.spotify.confidence;

import com.google.common.base.Strings;
import com.google.protobuf.Struct;
import com.spotify.confidence.Confidence.ConfidenceMetadata;
import com.spotify.confidence.shaded.flags.resolver.v1.*;
import com.spotify.confidence.shaded.flags.resolver.v1.Sdk.Builder;
import io.grpc.ManagedChannel;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class GrpcFlagResolver implements FlagResolver {
  private final ManagedChannel managedChannel;
  private final String clientSecret;
  private final Builder sdkBuilder = Sdk.newBuilder().setVersion(ConfidenceUtils.getSdkVersion());
  private final ConfidenceMetadata metadata;

  private final FlagResolverServiceGrpc.FlagResolverServiceFutureStub stub;

  public GrpcFlagResolver(
      String clientSecret, ManagedChannel managedChannel, ConfidenceMetadata metadata) {
    if (Strings.isNullOrEmpty(clientSecret)) {
      throw new IllegalArgumentException("clientSecret must be a non-empty string.");
    }
    this.clientSecret = clientSecret;
    this.managedChannel = managedChannel;
    this.metadata = metadata;
    this.stub = FlagResolverServiceGrpc.newFutureStub(managedChannel);
  }

  public CompletableFuture<ResolveFlagsResponse> resolve(
      String flag, Struct context, String providerId) {
    return GrpcUtil.toCompletableFuture(
        stub.withDeadlineAfter(10, TimeUnit.SECONDS)
            .resolveFlags(
                ResolveFlagsRequest.newBuilder()
                    .setClientSecret(this.clientSecret)
                    .addAllFlags(List.of(flag))
                    .setEvaluationContext(context)
                    .setSdk(getSdkId(metadata, providerId))
                    .setApply(true)
                    .build()));
  }

  private Sdk getSdkId(ConfidenceMetadata metadata, String providerId) {
    try {
      sdkBuilder.setId(SdkId.valueOf(providerId));
    } catch (IllegalArgumentException e) {
      sdkBuilder.setCustomId(providerId);
    }
    return sdkBuilder.build();
  }

  public void close() {
    managedChannel.shutdownNow();
  }
}
