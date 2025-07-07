package com.spotify.confidence.flags.resolver;

import static io.grpc.protobuf.services.HealthStatusManager.SERVICE_NAME_ALL_SERVICES;

import io.grpc.health.v1.HealthCheckResponse;
import io.grpc.protobuf.services.HealthStatusManager;
import java.util.concurrent.atomic.AtomicReference;

public class HealthStatus {

  private final HealthStatusManager healthStatusManager;
  private final AtomicReference<HealthCheckResponse.ServingStatus> status =
      new AtomicReference<>(HealthCheckResponse.ServingStatus.NOT_SERVING);

  public HealthStatus(HealthStatusManager healthStatusManager) {
    this.healthStatusManager = healthStatusManager;
    healthStatusManager.setStatus(SERVICE_NAME_ALL_SERVICES, status.get());
  }

  public synchronized void setStatus(HealthCheckResponse.ServingStatus status) {
    this.status.set(status);
    healthStatusManager.setStatus(SERVICE_NAME_ALL_SERVICES, status);
  }

  public HealthCheckResponse.ServingStatus get() {
    return status.get();
  }
}
