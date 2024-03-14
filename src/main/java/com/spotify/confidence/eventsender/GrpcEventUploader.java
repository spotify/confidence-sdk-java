package com.spotify.confidence.eventsender;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.protobuf.Struct;
import com.google.protobuf.Timestamp;
import com.spotify.confidence.events.v1.Event;
import com.spotify.confidence.events.v1.EventsServiceGrpc;
import com.spotify.confidence.events.v1.PublishEventsRequest;
import com.spotify.confidence.events.v1.PublishEventsResponse;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class GrpcEventUploader implements EventUploader {

  private final String clientSecret;
  private final ManagedChannel managedChannel;
  private final EventsServiceGrpc.EventsServiceFutureStub stub;
  private final Clock clock;

  public GrpcEventUploader(String clientSecret) {
    this(
        clientSecret,
        new SystemClock(),
        ManagedChannelBuilder.forAddress("edge-grpc.spotify.com", 443).build());
  }

  public GrpcEventUploader(String clientSecret, Clock clock) {
    this(
        clientSecret,
        clock,
        ManagedChannelBuilder.forAddress("edge-grpc.spotify.com", 443).build());
  }

  public GrpcEventUploader(String clientSecret, String host, int port) {
    this(clientSecret, new SystemClock(), ManagedChannelBuilder.forAddress(host, port).build());
  }

  public GrpcEventUploader(String clientSecret, Clock clock, ManagedChannel managedChannel) {
    this.clientSecret = clientSecret;
    this.managedChannel = managedChannel;
    this.stub = EventsServiceGrpc.newFutureStub(managedChannel);
    this.clock = clock;
  }

  @Override
  public CompletableFuture<Boolean> upload(EventBatch batch) {
    PublishEventsRequest request =
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
                                        .putFields("message", event.message().toProto())
                                        .putFields("context", event.context().toProto()))
                                .build())
                    .collect(Collectors.toList()))
            .build();
    ListenableFuture<PublishEventsResponse> response =
        stub.withDeadlineAfter(5, TimeUnit.SECONDS).publishEvents(request);

    try {
      response.get();
    } catch (ExecutionException | InterruptedException e) {
      e.printStackTrace();
      return CompletableFuture.completedFuture(false);
    }
    return CompletableFuture.completedFuture(true);
  }

  @Override
  public void close() {
    managedChannel.shutdown();
  }
}
