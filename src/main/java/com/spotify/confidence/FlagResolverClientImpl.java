package com.spotify.confidence;

import static com.spotify.confidence.ConfidenceFeatureProvider.OPEN_FEATURE_RESOLVE_CONTEXT_KEY;

import com.google.common.base.Strings;
import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import com.spotify.confidence.shaded.flags.resolver.v1.*;
import io.grpc.ManagedChannel;
import java.io.Closeable;
import java.io.IOException;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

class FlagResolverClientImpl implements FlagResolverClient, Closeable {

  // Deadline in seconds
  public static final int DEADLINE_AFTER_SECONDS = 10;
  private final ManagedChannel managedChannel;

  private final String clientSecret;

  private final String SDK_VERSION;
  private static final SdkId SDK_ID = SdkId.SDK_ID_JAVA_PROVIDER;

  private final FlagResolverServiceGrpc.FlagResolverServiceFutureStub stub;

  public FlagResolverClientImpl(String clientSecret, ManagedChannel managedChannel) {
    this.clientSecret = clientSecret;
    this.managedChannel = managedChannel;
    this.stub = FlagResolverServiceGrpc.newFutureStub(managedChannel);

    if (Strings.isNullOrEmpty(clientSecret)) {
      throw new IllegalArgumentException("clientSecret must be a non-empty string.");
    }

    try {
      final Properties prop = new Properties();
      prop.load(this.getClass().getResourceAsStream("/version.properties"));
      this.SDK_VERSION = prop.getProperty("version");
    } catch (IOException e) {
      throw new RuntimeException("Can't determine version of the SDK", e);
    }
  }

  public CompletableFuture<ResolveFlagsResponse> resolveFlags(
      String flagName, ConfidenceValue.Struct context) {
    final Struct.Builder evaluationContextBuilder = context.toProto().getStructValue().toBuilder();
    if (context.asMap().containsKey(OPEN_FEATURE_RESOLVE_CONTEXT_KEY)) {
      final Value openFeatureEvaluationContext =
          context.asMap().get(OPEN_FEATURE_RESOLVE_CONTEXT_KEY).toProto();

      evaluationContextBuilder.putAllFields(
          openFeatureEvaluationContext.getStructValue().getFieldsMap());
      evaluationContextBuilder.removeFields(OPEN_FEATURE_RESOLVE_CONTEXT_KEY);
    }

    return GrpcUtil.toCompletableFuture(
        stub.withDeadlineAfter(DEADLINE_AFTER_SECONDS, TimeUnit.SECONDS)
            .resolveFlags(
                ResolveFlagsRequest.newBuilder()
                    .setClientSecret(clientSecret)
                    .addAllFlags(List.of(flagName))
                    .setEvaluationContext(evaluationContextBuilder.build())
                    .setSdk(Sdk.newBuilder().setId(SDK_ID).setVersion(SDK_VERSION).build())
                    .setApply(true)
                    .build()));
  }

  @Override
  public void close() {
    managedChannel.shutdown();
  }
}
