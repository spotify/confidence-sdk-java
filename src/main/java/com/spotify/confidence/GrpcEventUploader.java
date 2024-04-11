package com.spotify.confidence;

import com.google.protobuf.Timestamp;
import com.spotify.confidence.events.v1.Event;
import com.spotify.confidence.events.v1.EventsServiceGrpc;
import com.spotify.confidence.events.v1.PublishEventsRequest;
import com.spotify.confidence.events.v1.Sdk;
import com.spotify.confidence.events.v1.SdkId;
import io.grpc.ManagedChannel;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.slf4j.Logger;

class GrpcEventUploader implements EventUploader {

  static final String CONTEXT = "context";
  private final String clientSecret;
  private final Sdk sdk;
  private final ManagedChannel managedChannel;
  private final EventsServiceGrpc.EventsServiceFutureStub stub;
  private final Clock clock;

  private static final Logger log = org.slf4j.LoggerFactory.getLogger(GrpcEventUploader.class);

  GrpcEventUploader(String clientSecret, Clock clock, ManagedChannel managedChannel) {
    this.clientSecret = clientSecret;
    this.managedChannel = managedChannel;
    this.stub = EventsServiceGrpc.newFutureStub(managedChannel);
    this.clock = clock;
    this.sdk =
        Sdk.newBuilder()
            .setId(SdkId.SDK_ID_JAVA_CONFIDENCE)
            .setVersion(SdkUtils.getSdkVersion())
            .build();
  }

  @Override
  public CompletableFuture<Boolean> upload(List<Event> events) {
    final PublishEventsRequest request =
        PublishEventsRequest.newBuilder()
            .setClientSecret(clientSecret)
            .setSendTime(Timestamp.newBuilder().setSeconds(clock.currentTimeSeconds()))
            .setSdk(sdk)
            .addAllEvents(events)
            .build();

    return GrpcUtil.toCompletableFuture(
            stub.withDeadlineAfter(5, TimeUnit.SECONDS).publishEvents(request))
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
              return false;
            }));
  }

  @Override
  public void close() {
    managedChannel.shutdown();
  }
}
