package com.spotify.confidence;

import com.spotify.confidence.shaded.flags.resolver.v1.InternalFlagLoggerServiceGrpc;
import com.spotify.confidence.shaded.flags.resolver.v1.WriteFlagLogsRequest;
import com.spotify.confidence.shaded.flags.resolver.v1.WriteFlagLogsResponse;
import com.spotify.confidence.shaded.iam.v1.AuthServiceGrpc;
import io.grpc.Channel;
import io.grpc.ClientInterceptors;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.time.Duration;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GrpcWasmFlagLogger implements WasmFlagLogger {
  private static final String CONFIDENCE_DOMAIN = "edge-grpc.spotify.com";
  private static final Logger logger = LoggerFactory.getLogger(GrpcWasmFlagLogger.class);
  private final InternalFlagLoggerServiceGrpc.InternalFlagLoggerServiceBlockingStub stub;

  public GrpcWasmFlagLogger(ApiSecret apiSecret) {
    final var channel = createConfidenceChannel();
    final AuthServiceGrpc.AuthServiceBlockingStub authService =
        AuthServiceGrpc.newBlockingStub(channel);
    final TokenHolder tokenHolder =
        new TokenHolder(apiSecret.clientId(), apiSecret.clientSecret(), authService);
    final TokenHolder.Token token = tokenHolder.getToken();
    final Channel authenticatedChannel =
        ClientInterceptors.intercept(channel, new JwtAuthClientInterceptor(tokenHolder));
    this.stub = InternalFlagLoggerServiceGrpc.newBlockingStub(authenticatedChannel);
  }

  @Override
  public WriteFlagLogsResponse write(WriteFlagLogsRequest request) {
    return stub.writeFlagLogs(request);
  }

  private static ManagedChannel createConfidenceChannel() {
    final String confidenceDomain =
        Optional.ofNullable(System.getenv("CONFIDENCE_DOMAIN")).orElse(CONFIDENCE_DOMAIN);
    final boolean useGrpcPlaintext =
        Optional.ofNullable(System.getenv("CONFIDENCE_GRPC_PLAINTEXT"))
            .map(Boolean::parseBoolean)
            .orElse(false);
    ManagedChannelBuilder<?> builder = ManagedChannelBuilder.forTarget(confidenceDomain);
    if (useGrpcPlaintext) {
      builder = builder.usePlaintext();
    }
    return builder.intercept(new DefaultDeadlineClientInterceptor(Duration.ofMinutes(1))).build();
  }
}
