package com.spotify.confidence;

import com.google.protobuf.Struct;
import com.google.protobuf.Timestamp;
import com.spotify.confidence.events.v1.Event;
import com.spotify.confidence.events.v1.EventsServiceGrpc;
import com.spotify.confidence.events.v1.PublishEventsRequest;
import io.grpc.ManagedChannel;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

class GrpcEventUploader implements EventUploader {

  private static final String CONTEXT = "context";
  private final String clientSecret;
  private final ManagedChannel managedChannel;
  private final EventsServiceGrpc.EventsServiceFutureStub stub;
  private final Clock clock;

  GrpcEventUploader(String clientSecret, Clock clock, ManagedChannel managedChannel) {
    this.clientSecret = clientSecret;
    this.managedChannel = managedChannel;
    this.stub = EventsServiceGrpc.newFutureStub(managedChannel);
    this.clock = clock;
  }

  @Override
  public CompletableFuture<Boolean> upload(EventBatch batch) {
    final PublishEventsRequest request =
        PublishEventsRequest.newBuilder()
            .setClientSecret(clientSecret)
            .setSendTime(Timestamp.newBuilder().setSeconds(clock.currentTimeSeconds()))
            .addAllEvents(
                batch.events().stream()
                    .map(
                        (event) ->
                            Event.newBuilder()
                                .setEventDefinition(event.name())
                                .setEventTime(Timestamp.newBuilder().setSeconds(event.emitTime()))
                                .setPayload(
                                    Struct.newBuilder()
                                        .putAllFields(event.message().asProtoMap())
                                        .putFields(CONTEXT, event.context().toProto()))
                                .build())
                    .collect(Collectors.toList()))
            .build();

    return GrpcUtil.toCompletableFuture(
            stub.withDeadlineAfter(5, TimeUnit.SECONDS).publishEvents(request))
        .thenApply(publishEventsResponse -> true)
        .exceptionally(
            (throwable -> {
              // TODO update to use some user-configurable logging
              throwable.printStackTrace();
              return false;
            }));
  }

  @Override
  public void close() {
    managedChannel.shutdown();
  }
}
