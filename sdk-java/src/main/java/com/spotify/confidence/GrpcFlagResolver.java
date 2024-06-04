package com.spotify.confidence;

import com.google.common.base.Strings;
import com.google.protobuf.Struct;
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

  private final FlagResolverServiceGrpc.FlagResolverServiceFutureStub stub;

  public GrpcFlagResolver(String clientSecret, ManagedChannel managedChannel) {
    if (Strings.isNullOrEmpty(clientSecret)) {
      throw new IllegalArgumentException("clientSecret must be a non-empty string.");
    }
    this.clientSecret = clientSecret;
    this.managedChannel = managedChannel;
    this.stub = FlagResolverServiceGrpc.newFutureStub(managedChannel);
  }

  public CompletableFuture<ResolveFlagsResponse> resolve(
      String flag, Struct context, Boolean isProvider) {
    return GrpcUtil.toCompletableFuture(
        stub.withDeadlineAfter(10, TimeUnit.SECONDS)
            .resolveFlags(
                ResolveFlagsRequest.newBuilder()
                    .setClientSecret(this.clientSecret)
                    .addAllFlags(List.of(flag))
                    .setEvaluationContext(context)
                    .setSdk(
                        sdkBuilder
                            .setId(
                                isProvider
                                    ? SdkId.SDK_ID_JAVA_PROVIDER
                                    : SdkId.SDK_ID_JAVA_CONFIDENCE)
                            .build())
                    .setApply(true)
                    .build()));
  }

  public void close() {
    managedChannel.shutdownNow();
  }
}
