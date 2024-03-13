package com.spotify.confidence.eventsender;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.protobuf.Struct;
import com.google.protobuf.Timestamp;
import com.google.protobuf.Value;
import com.spotify.confidence.events.v1.Event;
import com.spotify.confidence.events.v1.EventsServiceGrpc;
import com.spotify.confidence.events.v1.PublishEventsRequest;
import com.spotify.confidence.events.v1.PublishEventsResponse;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.util.HashMap;
import java.util.Map;
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
                                        .putAllFields(toProtoMap(event.context().value()))
                                        .putAllFields(toProtoMap(event.message().value()))
                                        .build())
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

  private Map<String, Value> toProtoMap(Map<String, ConfidenceValue> confidenceValueMap) {
    Map<String, Value> result = new HashMap<>();
    confidenceValueMap.forEach((key, value) -> result.put(key, toValue(value)));
    return result;
  }

  private Value toValue(ConfidenceValue value) {
    if (value instanceof ConfidenceValue.String) {
      return Value.newBuilder().setStringValue(((ConfidenceValue.String) value).value()).build();
    } else if (value instanceof ConfidenceValue.Struct) {
      return Value.newBuilder()
          .setStructValue(
              Struct.newBuilder()
                  .putAllFields(toProtoMap(((ConfidenceValue.Struct) value).value()))
                  .build())
          .build();
    }
    throw new IllegalArgumentException("Unsupported type: " + value.getClass());
  }
}
