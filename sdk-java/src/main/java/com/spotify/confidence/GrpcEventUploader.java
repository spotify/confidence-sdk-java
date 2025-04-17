package com.spotify.confidence;

import com.google.common.collect.ImmutableSet;
import com.spotify.confidence.events.v1.Event;
import com.spotify.confidence.events.v1.EventsServiceGrpc;
import com.spotify.confidence.events.v1.PublishEventsRequest;
import com.spotify.confidence.events.v1.Sdk;
import com.spotify.confidence.events.v1.SdkId;
import io.grpc.ManagedChannel;
import io.grpc.Status;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.slf4j.Logger;

class GrpcEventUploader implements EventUploader {

  private final Set<Status.Code> RETRYABLE_STATUS_CODES =
      ImmutableSet.of(
          Status.Code.UNKNOWN,
          Status.Code.DEADLINE_EXCEEDED,
          Status.Code.RESOURCE_EXHAUSTED,
          Status.Code.UNAVAILABLE,
          Status.Code.ABORTED,
          Status.Code.INTERNAL,
          Status.Code.DATA_LOSS);
  private final String clientSecret;
  private final Sdk sdk;
  private final ManagedChannel managedChannel;
  private final EventsServiceGrpc.EventsServiceFutureStub stub;
  private final Clock clock;
  private final int deadlineMillis;

  private static final Logger log = org.slf4j.LoggerFactory.getLogger(GrpcEventUploader.class);

  GrpcEventUploader(
      String clientSecret, Clock clock, ManagedChannel managedChannel, int deadlineMillis) {
    this.clientSecret = clientSecret;
    this.managedChannel = managedChannel;
    this.stub = EventsServiceGrpc.newFutureStub(managedChannel);
    this.clock = clock;
    this.deadlineMillis = deadlineMillis;
    this.sdk =
        Sdk.newBuilder()
            .setId(SdkId.SDK_ID_JAVA_CONFIDENCE)
            .setVersion(ConfidenceUtils.getSdkVersion())
            .build();
  }

  @Override
  public CompletableFuture<Boolean> upload(List<Event> events) {
    final PublishEventsRequest request =
        PublishEventsRequest.newBuilder()
            .setClientSecret(clientSecret)
            .setSendTime(clock.getTimestamp())
            .setSdk(sdk)
            .addAllEvents(events)
            .build();

    return GrpcUtil.toCompletableFuture(
            stub.withDeadlineAfter(deadlineMillis, TimeUnit.MILLISECONDS).publishEvents(request))
        .thenApply(
            publishEventsResponse -> {
              final List<Event> eventsInRequest = request.getEventsList();
              if (publishEventsResponse.getErrorsCount() == 0) {
                log.debug(
                    String.format("Successfully published %d events", eventsInRequest.size()));
              } else {
                log.error(
                    String.format(
                        "Published batch with %d events, of which %d failed. Failed events are of type: %s",
                        eventsInRequest.size(),
                        publishEventsResponse.getErrorsCount(),
                        publishEventsResponse.getErrorsList().stream()
                            .map(e -> eventsInRequest.get(e.getIndex()).getEventDefinition())
                            .collect(Collectors.toSet())));
              }
              return true;
            })
        .exceptionally(
            (throwable -> {
              log.error(
                  String.format("Publishing batch failed with reason: %s", throwable.getMessage()),
                  throwable);
              return !RETRYABLE_STATUS_CODES.contains(Status.fromThrowable(throwable).getCode());
            }));
  }
}
