package com.spotify.confidence;

import static com.spotify.confidence.shaded.flags.resolver.v1.FlagResolverServiceGrpc.*;

import com.google.common.base.Strings;
import com.google.protobuf.Struct;
import io.grpc.ManagedChannel;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class GrpcFlagResolver implements FlagResolver {
  private final ManagedChannel managedChannel;
  private final String clientSecret;
  private final com.spotify.confidence.shaded.flags.resolver.v1.Sdk sdk;

  private final FlagResolverServiceFutureStub stub;

  public GrpcFlagResolver(String clientSecret, ManagedChannel managedChannel) {
    if (Strings.isNullOrEmpty(clientSecret)) {
      throw new IllegalArgumentException("clientSecret must be a non-empty string.");
    }
    this.clientSecret = clientSecret;
    this.sdk =
        com.spotify.confidence.shaded.flags.resolver.v1.Sdk.newBuilder()
            .setId(com.spotify.confidence.shaded.flags.resolver.v1.SdkId.SDK_ID_JAVA_PROVIDER)
            .setVersion(SdkUtils.getSdkVersion())
            .build();
    this.managedChannel = managedChannel;
    this.stub =
        com.spotify.confidence.shaded.flags.resolver.v1.FlagResolverServiceGrpc.newFutureStub(
            managedChannel);
  }

  public CompletableFuture<com.spotify.confidence.shaded.flags.resolver.v1.ResolveFlagsResponse>
      resolve(String flag, Struct context) {
    return GrpcUtil.toCompletableFuture(
        stub.withDeadlineAfter(10, TimeUnit.SECONDS)
            .resolveFlags(
                com.spotify.confidence.shaded.flags.resolver.v1.ResolveFlagsRequest.newBuilder()
                    .setClientSecret(this.clientSecret)
                    .addAllFlags(List.of(flag))
                    .setEvaluationContext(context)
                    .setSdk(sdk)
                    .setApply(true)
                    .build()));
  }

  public void close() {
    managedChannel.shutdownNow();
  }
}
