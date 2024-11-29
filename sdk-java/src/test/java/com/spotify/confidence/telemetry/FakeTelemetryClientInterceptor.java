package com.spotify.confidence.telemetry;

import com.spotify.confidence.Telemetry;
import com.spotify.confidence.TelemetryClientInterceptor;
import io.grpc.*;
import javax.annotation.Nullable;

public class FakeTelemetryClientInterceptor extends TelemetryClientInterceptor {

  private Metadata storedHeaders;

  public FakeTelemetryClientInterceptor(@Nullable Telemetry telemetry) {
    super(telemetry);
  }

  @Override
  public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
      MethodDescriptor<ReqT, RespT> method, CallOptions callOptions, Channel next) {
    return new ForwardingClientCall.SimpleForwardingClientCall<ReqT, RespT>(
        super.interceptCall(method, callOptions, next)) {
      @Override
      public void start(Listener<RespT> responseListener, Metadata headers) {
        storedHeaders = headers;
        super.start(responseListener, headers);
      }
    };
  }

  public Metadata getStoredHeaders() {
    return storedHeaders;
  }
}
