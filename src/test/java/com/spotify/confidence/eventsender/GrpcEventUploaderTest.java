package com.spotify.confidence.eventsender;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.ImmutableMap;
import com.google.protobuf.Value;
import com.spotify.confidence.events.v1.EventsServiceGrpc;
import com.spotify.confidence.events.v1.PublishEventsRequest;
import com.spotify.confidence.events.v1.PublishEventsResponse;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GrpcEventUploaderTest {

  private GrpcEventUploader uploader;
  private Server server;
  private ManagedChannel channel;
  private FakedEventsService fakedEventsService;

  @BeforeEach
  public void setUp() throws IOException {
    // Generate a unique in-process server name
    String serverName = InProcessServerBuilder.generateName();

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

    // Create a client that uses the channel
    uploader = new GrpcEventUploader("my-client-secret", channel);
  }

  @AfterEach
  public void tearDown() {
    fakedEventsService.clear();
    channel.shutdown();
    server.shutdown();
  }

  @Test
  public void testMapsSingleEventBatchToProtobuf() {
    EventBatch batch =
        new EventBatch(List.of(new Event("event1", messageStruct("1"), contextStruct("1"))));
    uploader.upload(batch);
    assertThat(fakedEventsService.requests).hasSize(1);

    PublishEventsRequest request = fakedEventsService.requests.get(0);
    assertThat(request.getEventsList()).hasSize(1);

    com.spotify.confidence.events.v1.Event protoEvent = request.getEvents(0);
    assertThat(protoEvent.getEventDefinition()).isEqualTo("event1");

    Map<String, Value> fieldsMap = protoEvent.getPayload().getFieldsMap();
    assertThat(fieldsMap.get("messageKey").getStringValue()).isEqualTo("value_1");
    assertThat(fieldsMap.get("contextKey").getStringValue()).isEqualTo("value_1");
  }

  @Test
  public void testMappingMessageTakesPrecedence() {
    ConfidenceValue.Struct message = contextStruct("keep");
    ConfidenceValue.Struct context = contextStruct("discard");
    // message and context now have the same key, but message should take precedence
    EventBatch batch = new EventBatch(List.of(new Event("event1", message, context)));
    uploader.upload(batch);
    assertThat(fakedEventsService.requests).hasSize(1);

    PublishEventsRequest request = fakedEventsService.requests.get(0);
    assertThat(request.getEventsList()).hasSize(1);

    com.spotify.confidence.events.v1.Event protoEvent = request.getEvents(0);
    assertThat(protoEvent.getEventDefinition()).isEqualTo("event1");

    Map<String, Value> fieldsMap = protoEvent.getPayload().getFieldsMap();
    assertThat(fieldsMap.get("contextKey").getStringValue()).isEqualTo("value_keep");
  }

  private ConfidenceValue.Struct contextStruct(String s) {
    return ConfidenceValue.of(ImmutableMap.of("contextKey", ConfidenceValue.of("value_" + s)));
  }

  private ConfidenceValue.Struct messageStruct(String s) {
    return ConfidenceValue.of(ImmutableMap.of("messageKey", ConfidenceValue.of("value_" + s)));
  }

  private static class FakedEventsService extends EventsServiceGrpc.EventsServiceImplBase {
    List<PublishEventsRequest> requests = new ArrayList<>();

    public void clear() {
      requests.clear();
    }

    @Override
    public void publishEvents(
        PublishEventsRequest request, StreamObserver<PublishEventsResponse> responseObserver) {
      requests.add(request);
      responseObserver.onNext(PublishEventsResponse.newBuilder().build());
      responseObserver.onCompleted();
    }
  }
}
