package com.spotify.confidence;

import static com.spotify.confidence.EventUploader.event;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.ImmutableMap;
import com.google.protobuf.Timestamp;
import com.spotify.confidence.events.v1.EventError;
import com.spotify.confidence.events.v1.EventError.Reason;
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
    uploader.upload(
        List.of(
            com.spotify.confidence.events.v1.Event.newBuilder()
                .setEventTime(Timestamp.newBuilder().setSeconds(1337))
                .build()));
    assertThat(fakedEventsService.requests).hasSize(1);

    final PublishEventsRequest request = fakedEventsService.requests.get(0);
    assertThat(request.getSendTime().getSeconds()).isEqualTo(1337);
  }

  @Test
  public void testMapsSingleEventBatchToProtobuf() throws ExecutionException, InterruptedException {
    final CompletableFuture<Boolean> completableFuture =
        uploader.upload(
            List.of(
                event("event1", contextStruct("1"), Optional.of(messageStruct("1")))
                    .setEventTime(Timestamp.newBuilder().setSeconds(1337))
                    .build()));
    final Boolean result = completableFuture.get();
    assertThat(result).isTrue();

    assertThat(fakedEventsService.requests).hasSize(1);

    final PublishEventsRequest request = fakedEventsService.requests.get(0);
    assertThat(request.getEventsList()).hasSize(1);

    final com.spotify.confidence.events.v1.Event protoEvent = request.getEvents(0);
    assertThat(protoEvent.getEventDefinition()).isEqualTo("eventDefinitions/event1");

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
    final var batch =
        List.of(
            event("event1", contextStruct("c1"), Optional.of(messageStruct("m1")))
                .setEventTime(Timestamp.newBuilder().setSeconds(1337))
                .build(),
            event("event2", contextStruct("c2"), Optional.of(messageStruct("m2")))
                .setEventTime(Timestamp.newBuilder().setSeconds(1338))
                .build(),
            event("event3", contextStruct("c3"), Optional.of(messageStruct("m3")))
                .setEventTime(Timestamp.newBuilder().setSeconds(1339))
                .build(),
            event("event4", contextStruct("c4"), Optional.of(messageStruct("m4")))
                .setEventTime(Timestamp.newBuilder().setSeconds(1340))
                .build());
    uploader.upload(batch);
    assertThat(fakedEventsService.requests).hasSize(1);

    final PublishEventsRequest request = fakedEventsService.requests.get(0);
    assertThat(request.getEventsList()).hasSize(4);

    for (int i = 0; i < batch.size(); i++) {
      final com.spotify.confidence.events.v1.Event protoEvent = request.getEvents(i);
      assertThat(protoEvent.getEventDefinition()).isEqualTo("eventDefinitions/event" + (i + 1));

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
  public void testMapsMultiEventBatchToProtobufSparseErrors()
      throws ExecutionException, InterruptedException {
    fakedEventsService.resultType = ResultType.FIRST_EVENT_ERROR;
    final var batch =
        List.of(
            event("event1", contextStruct("c1"), Optional.of(messageStruct("m1")))
                .setEventTime(Timestamp.newBuilder().setSeconds(1337))
                .build(),
            event("event2", contextStruct("c2"), Optional.of(messageStruct("m2")))
                .setEventTime(Timestamp.newBuilder().setSeconds(1338))
                .build(),
            event("event3", contextStruct("c3"), Optional.of(messageStruct("m3")))
                .setEventTime(Timestamp.newBuilder().setSeconds(1339))
                .build(),
            event("event4", contextStruct("c4"), Optional.of(messageStruct("m4")))
                .setEventTime(Timestamp.newBuilder().setSeconds(1340))
                .build());
    final CompletableFuture<Boolean> completableFuture = uploader.upload(batch);
    assertThat(fakedEventsService.requests).hasSize(1);
    final PublishEventsRequest request = fakedEventsService.requests.get(0);
    assertThat(request.getEventsList()).hasSize(4);
    final Boolean result = completableFuture.get();
    assertThat(result).isTrue();
  }

  @Test
  public void testServiceThrows() throws ExecutionException, InterruptedException {
    fakedEventsService.resultType = ResultType.REQUEST_ERROR;
    final var batch =
        List.of(
            event("event1", contextStruct("1"), Optional.of(messageStruct("1")))
                .setEventTime(Timestamp.newBuilder().setSeconds(1337))
                .build());
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

  private enum ResultType {
    REQUEST_ERROR,
    FIRST_EVENT_ERROR,
    SUCCESS
  }

  private static class FakedEventsService extends EventsServiceGrpc.EventsServiceImplBase {

    public ResultType resultType;
    final List<PublishEventsRequest> requests = new ArrayList<>();

    public void clear() {
      requests.clear();
      resultType = ResultType.SUCCESS;
    }

    @Override
    public void publishEvents(
        PublishEventsRequest request, StreamObserver<PublishEventsResponse> responseObserver) {
      requests.add(request);
      if (resultType == ResultType.REQUEST_ERROR) {
        responseObserver.onError(new RuntimeException("error"));
      } else if (resultType == ResultType.FIRST_EVENT_ERROR) {
        responseObserver.onNext(
            PublishEventsResponse.newBuilder()
                .addErrors(
                    0,
                    EventError.newBuilder()
                        .setReason(Reason.EVENT_SCHEMA_VALIDATION_FAILED)
                        .build())
                .build());
        responseObserver.onCompleted();
      } else {
        responseObserver.onNext(PublishEventsResponse.newBuilder().build());
        responseObserver.onCompleted();
      }
    }
  }
}
