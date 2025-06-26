package com.spotify.confidence;

import com.google.common.io.Closer;
import com.spotify.confidence.shaded.flags.resolver.v1.ResolveFlagsResponse;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class RemoteResolve implements ResolveMode {
  private final Closer closer;
  private final FlagResolverClient flagClient;

  public RemoteResolve(FlagResolverClient flagClient, Closer closer) {
    this.closer = closer;
    this.flagClient = flagClient;
  }

  public static Builder builder() {
    return new Builder();
  }

  @Override
  public void close() {
    try {
      this.closer.close();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public CompletableFuture<ResolveFlagsResponse> resolveFlags(
      String flag, ConfidenceValue.Struct context) {
    return flagClient.resolveFlags(flag, context);
  }

  public static class Builder {
    private boolean isProvider = false;
    private boolean disableTelemetry = false;
    private String clientSecret;
    private int resolveDeadlineMs = 10_000;
    private final ManagedChannel DEFAULT_CHANNEL =
        ManagedChannelBuilder.forAddress("edge-grpc.spotify.com", 443)
            .keepAliveTime(Duration.ofMinutes(5).getSeconds(), TimeUnit.SECONDS)
            .build();
    private ManagedChannel flagResolverManagedChannel = DEFAULT_CHANNEL;
    private final Closer closer = Closer.create();

    public Builder flagResolverManagedChannel(String host, int port) {
      this.flagResolverManagedChannel =
          ManagedChannelBuilder.forAddress(host, port).usePlaintext().build();
      registerChannelForShutdown(this.flagResolverManagedChannel);
      return this;
    }

    public Builder flagResolverManagedChannel(ManagedChannel managedChannel) {
      this.flagResolverManagedChannel = managedChannel;
      return this;
    }

    public Builder setClientSecret(String clientSecret) {
      this.clientSecret = clientSecret;
      return this;
    }

    public Builder setResolveDeadlineMs(int resolveDeadlineMs) {
      this.resolveDeadlineMs = resolveDeadlineMs;
      return this;
    }

    public Builder setIsProvider(boolean isProvider) {
      this.isProvider = isProvider;
      return this;
    }

    public Builder setDisableTelemetry(boolean disableTelemetry) {
      this.disableTelemetry = disableTelemetry;
      return this;
    }

    public RemoteResolve build() {
      final Telemetry telemetry = disableTelemetry ? null : new Telemetry(isProvider);
      final TelemetryClientInterceptor telemetryInterceptor =
          new TelemetryClientInterceptor(telemetry);
      final GrpcFlagResolver flagResolver =
          new GrpcFlagResolver(
              clientSecret, flagResolverManagedChannel, telemetryInterceptor, resolveDeadlineMs);
      final var flagResolverClient = new FlagResolverClientImpl(flagResolver, telemetry);
      closer.register(flagResolverClient);

      return new RemoteResolve(flagResolverClient, closer);
    }

    private void registerChannelForShutdown(ManagedChannel channel) {
      this.closer.register(
          () -> {
            channel.shutdown();
            try {
              channel.awaitTermination(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
              channel.shutdownNow();
            }
          });
    }
  }
}
