package com.spotify.confidence;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.ImmutableMap;
import com.spotify.confidence.events.v1.EventsServiceGrpc;
import com.spotify.confidence.events.v1.PublishEventsRequest;
import com.spotify.confidence.events.v1.PublishEventsResponse;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GrpcEventUploaderTest {
  private static final String CONTEXT = "context";
  private GrpcEventUploader uploader;
  private Server server;
  private ManagedChannel channel;
  private FakedEventsService fakedEventsService;

  private static FakeClock fakeClock = new FakeClock();

  @BeforeEach
  public void setUp() throws IOException {
    // Generate a unique in-process server name
    final String serverName = InProcessServerBuilder.generateName();

    // Create a service implementation
    fakedEventsService = new FakedEventsService();

    // Create and start an in-process server
    server =
        InProcessServerBuilder.forName(serverName)
            .directExecutor()
            .addService(fakedEventsService)
            .build()
            .start();

    // Create an in-process channel
    channel = InProcessChannelBuilder.forName(serverName).directExecutor().build();

    fakeClock.setCurrentTimeSeconds(1337);

    // Create a client that uses the channel
    uploader = new GrpcEventUploader("my-client-secret", fakeClock, channel);
  }

  @AfterEach
  public void tearDown() {
    fakedEventsService.clear();
    channel.shutdown();
    server.shutdown();
  }

  @Test
  public void testSendTime() {
    final EventBatch batch =
        new EventBatch(List.of(new Event("event1", messageStruct("1"), contextStruct("1"), 1337)));
    uploader.upload(batch);
    assertThat(fakedEventsService.requests).hasSize(1);

    final PublishEventsRequest request = fakedEventsService.requests.get(0);
    assertThat(request.getSendTime().getSeconds()).isEqualTo(1337);
  }

  @Test
  public void testMapsSingleEventBatchToProtobuf() throws ExecutionException, InterruptedException {
    final EventBatch batch =
        new EventBatch(List.of(new Event("event1", messageStruct("1"), contextStruct("1"), 1337)));
    final CompletableFuture<Boolean> completableFuture = uploader.upload(batch);
    final Boolean result = completableFuture.get();
    assertThat(result).isTrue();

    assertThat(fakedEventsService.requests).hasSize(1);

    final PublishEventsRequest request = fakedEventsService.requests.get(0);
    assertThat(request.getEventsList()).hasSize(1);

    final com.spotify.confidence.events.v1.Event protoEvent = request.getEvents(0);
    assertThat(protoEvent.getEventDefinition()).isEqualTo("event1");

    final Map<String, com.google.protobuf.Value> fieldsMap = protoEvent.getPayload().getFieldsMap();
    assertThat(fieldsMap.get("messageKey").getStringValue()).isEqualTo("value_1");
    assertThat(
            fieldsMap
                .get(CONTEXT)
                .getStructValue()
                .getFieldsMap()
                .get("contextKey")
                .getStringValue())
        .isEqualTo("value_1");
  }

  @Test
  public void testMapsMultiEventBatchToProtobuf() {
    final EventBatch batch =
        new EventBatch(
            List.of(
                new Event("event1", messageStruct("m1"), contextStruct("c1"), 1337),
                new Event("event2", messageStruct("m2"), contextStruct("c2"), 1338),
                new Event("event3", messageStruct("m3"), contextStruct("c3"), 1339),
                new Event("event4", messageStruct("m4"), contextStruct("c4"), 1340)));
    uploader.upload(batch);
    assertThat(fakedEventsService.requests).hasSize(1);

    final PublishEventsRequest request = fakedEventsService.requests.get(0);
    assertThat(request.getEventsList()).hasSize(4);

    for (int i = 0; i < batch.events().size(); i++) {
      final com.spotify.confidence.events.v1.Event protoEvent = request.getEvents(i);
      assertThat(protoEvent.getEventDefinition()).isEqualTo("event" + (i + 1));

      final Map<String, com.google.protobuf.Value> fieldsMap =
          protoEvent.getPayload().getFieldsMap();
      assertThat(fieldsMap.get("messageKey").getStringValue()).isEqualTo("value_m" + (i + 1));
      assertThat(
              fieldsMap
                  .get(CONTEXT)
                  .getStructValue()
                  .getFieldsMap()
                  .get("contextKey")
                  .getStringValue())
          .isEqualTo("value_c" + (i + 1));
    }
  }

  @Test
  public void testServiceThrows() throws ExecutionException, InterruptedException {
    fakedEventsService.shouldError = true;
    final EventBatch batch =
        new EventBatch(List.of(new Event("event1", messageStruct("1"), contextStruct("1"), 1337)));
    final CompletableFuture<Boolean> completableFuture = uploader.upload(batch);
    assertThat(fakedEventsService.requests).hasSize(1);
    final Boolean result = completableFuture.get();
    assertThat(result).isFalse();
  }

  private ConfidenceValue.Struct contextStruct(String s) {
    return ConfidenceValue.of(ImmutableMap.of("contextKey", ConfidenceValue.of("value_" + s)));
  }

  private ConfidenceValue.Struct messageStruct(String s) {
    return ConfidenceValue.of(ImmutableMap.of("messageKey", ConfidenceValue.of("value_" + s)));
  }

  private static class FakedEventsService extends EventsServiceGrpc.EventsServiceImplBase {
    public boolean shouldError;
    final List<PublishEventsRequest> requests = new ArrayList<>();

    public void clear() {
      requests.clear();
      shouldError = false;
    }

    @Override
    public void publishEvents(
        PublishEventsRequest request, StreamObserver<PublishEventsResponse> responseObserver) {
      requests.add(request);
      if (shouldError) {
        responseObserver.onError(new RuntimeException("error"));
      } else {
        responseObserver.onNext(PublishEventsResponse.newBuilder().build());
        responseObserver.onCompleted();
      }
    }
  }
}
