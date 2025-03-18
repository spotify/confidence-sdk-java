package com.spotify.confidence;

import com.spotify.telemetry.v1.Types.Monitoring;
import io.grpc.*;
import java.util.Base64;
import javax.annotation.Nullable;

public class TelemetryClientInterceptor implements ClientInterceptor {
  public static final Metadata.Key<String> HEADER_KEY =
      Metadata.Key.of("X-CONFIDENCE-TELEMETRY", Metadata.ASCII_STRING_MARSHALLER);
  private final @Nullable Telemetry telemetry;

  public TelemetryClientInterceptor(@Nullable Telemetry telemetry) {
    this.telemetry = telemetry;
  }

  @Override
  public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
      MethodDescriptor<ReqT, RespT> method, CallOptions callOptions, Channel next) {
    return new ForwardingClientCall.SimpleForwardingClientCall<>(
        next.newCall(method, callOptions)) {
      @Override
      public void start(Listener<RespT> responseListener, Metadata headers) {
        if (telemetry != null) {
          final Monitoring telemetrySnapshot = telemetry.getSnapshot();
          final String base64Telemetry =
              Base64.getEncoder().encodeToString(telemetrySnapshot.toByteArray());
          headers.put(HEADER_KEY, base64Telemetry);
        }
        super.start(responseListener, headers);
      }
    };
  }
}
