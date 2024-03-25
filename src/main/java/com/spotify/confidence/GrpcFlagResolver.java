package com.spotify.confidence;

import com.google.common.base.Strings;
import com.google.protobuf.Struct;
import com.spotify.confidence.shaded.flags.resolver.v1.*;
import io.grpc.ManagedChannel;
import java.io.IOException;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class GrpcFlagResolver implements FlagResolver {
  private final ManagedChannel managedChannel;
  private final String clientSecret;
  private final String SDK_VERSION;
  private static final SdkId SDK_ID = SdkId.SDK_ID_JAVA_PROVIDER;

  private final FlagResolverServiceGrpc.FlagResolverServiceFutureStub stub;

  public GrpcFlagResolver(String clientSecret, ManagedChannel managedChannel) {
    if (Strings.isNullOrEmpty(clientSecret)) {
      throw new IllegalArgumentException("clientSecret must be a non-empty string.");
    }

    this.clientSecret = clientSecret;

    try {
      final Properties prop = new Properties();
      prop.load(this.getClass().getResourceAsStream("/version.properties"));
      this.SDK_VERSION = prop.getProperty("version");
    } catch (IOException e) {
      throw new RuntimeException("Can't determine version of the SDK", e);
    }

    this.managedChannel = managedChannel;
    this.stub = FlagResolverServiceGrpc.newFutureStub(managedChannel);
  }

  public CompletableFuture<ResolveFlagsResponse> resolve(String flag, Struct context) {
    return GrpcUtil.toCompletableFuture(
        stub.withDeadlineAfter(10, TimeUnit.SECONDS)
            .resolveFlags(
                ResolveFlagsRequest.newBuilder()
                    .setClientSecret(this.clientSecret)
                    .addAllFlags(List.of(flag))
                    .setEvaluationContext(context)
                    .setSdk(Sdk.newBuilder().setId(SDK_ID).setVersion(SDK_VERSION).build())
                    .setApply(true)
                    .build()));
  }

  public void close() {
    managedChannel.shutdownNow();
  }
}
