package com.spotify.confidence;

import static com.spotify.confidence.ResolverClientTestUtils.generateSampleResponse;
import static dev.openfeature.sdk.ErrorCode.GENERAL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.google.protobuf.util.Structs;
import com.google.protobuf.util.Values;
import com.spotify.confidence.ResolverClientTestUtils.ValueSchemaHolder;
import com.spotify.confidence.shaded.flags.resolver.v1.FlagResolverServiceGrpc.FlagResolverServiceImplBase;
import com.spotify.confidence.shaded.flags.resolver.v1.ResolveFlagsRequest;
import com.spotify.confidence.shaded.flags.resolver.v1.ResolveFlagsResponse;
import com.spotify.confidence.shaded.flags.resolver.v1.ResolvedFlag;
import com.spotify.confidence.shaded.flags.types.v1.FlagSchema;
import com.spotify.confidence.telemetry.Telemetry;
import com.spotify.confidence.telemetry.TelemetryClientInterceptor;
import com.spotify.telemetry.v1.LibraryTraces;
import com.spotify.telemetry.v1.Monitoring;
import dev.openfeature.sdk.*;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.Status;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

final class FeatureProviderTest {
  private static Server server;
  private static ManagedChannel channel;
  private static final Value DEFAULT_VALUE = new Value("string-default");
  private static final FlagResolverServiceImplBase serviceImpl =
      mock(FlagResolverServiceImplBase.class);
  private static Client client;
  private static OpenFeatureAPI openFeatureAPI;

  private static final EvaluationContext SAMPLE_CONTEXT_WITHOUT_TARGETING_KEY =
      new MutableContext(Map.of("my-key", new Value(true)));

  private static final EvaluationContext SAMPLE_CONTEXT_2_TARGETING_KEYS =
      new MutableContext(
          "my-targeting-key-1",
          Map.of(OpenFeatureUtils.TARGETING_KEY, new Value("my-targeting-key-2")));

  private static final EvaluationContext SAMPLE_CONTEXT =
      new MutableContext("my-targeting-key", Map.of("my-key", new Value(true)));

  static final String serverName = InProcessServerBuilder.generateName();
  private Telemetry telemetry;

  @BeforeAll
  static void before() throws IOException {

    server =
        InProcessServerBuilder.forName(serverName)
            .directExecutor()
            .addService(serviceImpl)
            .build()
            .start();
  }

  @BeforeEach
  void beforeEach() {
    final FakeEventSenderEngine fakeEventSender = new FakeEventSenderEngine(new FakeClock());
    channel = InProcessChannelBuilder.forName(serverName).directExecutor().build();
    telemetry = new Telemetry();
    final TelemetryClientInterceptor telemetryInterceptor =
        new TelemetryClientInterceptor(telemetry);
    final FlagResolverClientImpl flagResolver =
        new FlagResolverClientImpl(
            new GrpcFlagResolver("fake-secret", channel, telemetryInterceptor), telemetry);
    final Confidence confidence = Confidence.create(fakeEventSender, flagResolver);
    final FeatureProvider featureProvider = new ConfidenceFeatureProvider(confidence);

    openFeatureAPI = OpenFeatureAPI.getInstance();
    openFeatureAPI.setProvider(featureProvider);

    client = openFeatureAPI.getClient();
  }

  @AfterAll
  static void after() {
    channel.shutdownNow();
    server.shutdownNow();
  }

  @Test
  public void nonExistingFlag() {

    mockResolve(
        (request, streamObserver) -> {
          streamObserver.onNext(ResolveFlagsResponse.getDefaultInstance());
          streamObserver.onCompleted();
        });

    final FlagEvaluationDetails<Value> evaluationDetails =
        client.getObjectDetails("not-existing", DEFAULT_VALUE, SAMPLE_CONTEXT);

    assertThat(evaluationDetails.getValue()).isEqualTo(DEFAULT_VALUE);
    assertThat(evaluationDetails.getErrorCode()).isEqualTo(ErrorCode.FLAG_NOT_FOUND);
    assertThat(evaluationDetails.getReason()).isEqualTo("ERROR");
    assertThat(evaluationDetails.getVariant()).isBlank();
    assertThat(evaluationDetails.getErrorMessage())
        .isEqualTo("No active flag 'not-existing' was found");
  }

  @Test
  public void unexpectedFlag() {

    mockResolve(
        (request, streamObserver) -> {
          streamObserver.onNext(
              ResolveFlagsResponse.newBuilder()
                  .addResolvedFlags(
                      ResolvedFlag.newBuilder().setFlag("flags/unexpected-flag").build())
                  .build());
          streamObserver.onCompleted();
        });

    final FlagEvaluationDetails<Value> evaluationDetails =
        client.getObjectDetails("flag", DEFAULT_VALUE, SAMPLE_CONTEXT);

    assertThat(evaluationDetails.getValue()).isEqualTo(DEFAULT_VALUE);
    assertThat(evaluationDetails.getErrorCode()).isEqualTo(ErrorCode.FLAG_NOT_FOUND);
    assertThat(evaluationDetails.getReason()).isEqualTo("ERROR");
    assertThat(evaluationDetails.getVariant()).isBlank();
    assertThat(evaluationDetails.getErrorMessage())
        .isEqualTo("Unexpected flag 'unexpected-flag' from remote");
  }

  @Test
  public void unavailableApi() {

    mockResolve(
        (request, streamObserver) -> streamObserver.onError(Status.UNAVAILABLE.asException()));

    final FlagEvaluationDetails<Value> evaluationDetails =
        client.getObjectDetails("flags/whatever", DEFAULT_VALUE, SAMPLE_CONTEXT);

    assertThat(evaluationDetails.getValue()).isEqualTo(DEFAULT_VALUE);
    assertThat(evaluationDetails.getErrorCode()).isEqualTo(GENERAL);
    assertThat(evaluationDetails.getReason()).isEqualTo("ERROR");
    assertThat(evaluationDetails.getErrorMessage()).isEqualTo("Provider backend is unavailable");
    assertThat(evaluationDetails.getVariant()).isBlank();
  }

  @Test
  public void unauthenticated() {

    mockResolve(
        (request, streamObserver) -> streamObserver.onError(Status.UNAUTHENTICATED.asException()));

    final FlagEvaluationDetails<Value> evaluationDetails =
        client.getObjectDetails("flags/whatever", DEFAULT_VALUE, SAMPLE_CONTEXT);

    assertThat(evaluationDetails.getValue()).isEqualTo(DEFAULT_VALUE);
    assertThat(evaluationDetails.getErrorCode()).isEqualTo(GENERAL);
    assertThat(evaluationDetails.getReason()).isEqualTo("ERROR");
    assertThat(evaluationDetails.getErrorMessage()).isEqualTo("UNAUTHENTICATED");
    assertThat(evaluationDetails.getVariant()).isBlank();
  }

  @Test
  public void lackOfAssignment() {

    mockResolve(
        (request, streamObserver) -> {
          streamObserver.onNext(
              ResolveFlagsResponse.newBuilder()
                  .addResolvedFlags(ResolvedFlag.newBuilder().setFlag("flags/whatever").build())
                  .build());
          streamObserver.onCompleted();
        });

    final FlagEvaluationDetails<Value> evaluationDetails =
        client.getObjectDetails("whatever", DEFAULT_VALUE, SAMPLE_CONTEXT);

    assertThat(evaluationDetails.getValue()).isEqualTo(DEFAULT_VALUE);
    assertThat(evaluationDetails.getErrorCode()).isNull();
    assertThat(evaluationDetails.getErrorMessage()).isBlank();
    assertThat(evaluationDetails.getReason())
        .startsWith("The server returned no assignment for the flag");
    assertThat(evaluationDetails.getVariant()).isBlank();
  }

  @Test
  public void regularResolve() {

    mockResolve(
        (ResolveFlagsRequest, streamObserver) -> {
          assertThat(ResolveFlagsRequest.getFlags(0)).isEqualTo("flags/flag");

          assertThat(ResolveFlagsRequest.getEvaluationContext())
              .isEqualTo(
                  Structs.of(
                      "my-key", Values.of(true), "targeting_key", Values.of("my-targeting-key")));

          streamObserver.onNext(generateSampleResponse(Collections.emptyList()));
          streamObserver.onCompleted();
        });

    final FlagEvaluationDetails<Value> evaluationDetails =
        client.getObjectDetails("flag", DEFAULT_VALUE, SAMPLE_CONTEXT);

    assertThat(evaluationDetails.getErrorCode()).isNull();
    assertThat(evaluationDetails.getVariant()).isEqualTo("flags/flag/variants/var-A");
    assertThat(evaluationDetails.getErrorMessage()).isBlank();
    assertThat(evaluationDetails.getValue())
        .isEqualTo(
            new Value(
                new MutableStructure(
                    Map.of(
                        "prop-A",
                        new Value(false),
                        "prop-B",
                        new Value(
                            new MutableStructure(
                                Map.of("prop-C", new Value("str-val"), "prop-D", new Value(5.3)))),
                        "prop-E",
                        new Value(50),
                        "prop-F",
                        new Value(List.of(new Value("a"), new Value("b"))),
                        "prop-G",
                        new Value(
                            new MutableStructure(
                                Map.of(
                                    "prop-H", new Value(),
                                    "prop-I", new Value())))))));
  }

  @Test
  public void regularResolveWithoutTargetingKey() {

    mockResolve(
        (ResolveFlagsRequest, streamObserver) -> {
          assertThat(ResolveFlagsRequest.getFlags(0)).isEqualTo("flags/flag");

          assertThat(ResolveFlagsRequest.getEvaluationContext())
              .isEqualTo(Structs.of("my-key", Values.of(true)));

          streamObserver.onNext(generateSampleResponse(Collections.emptyList()));
          streamObserver.onCompleted();
        });

    final FlagEvaluationDetails<Value> evaluationDetails =
        client.getObjectDetails("flag", DEFAULT_VALUE, SAMPLE_CONTEXT_WITHOUT_TARGETING_KEY);

    assertThat(evaluationDetails.getErrorCode()).isNull();
    assertThat(evaluationDetails.getVariant()).isEqualTo("flags/flag/variants/var-A");
    assertThat(evaluationDetails.getErrorMessage()).isBlank();
    assertThat(evaluationDetails.getValue())
        .isEqualTo(
            new Value(
                new MutableStructure(
                    Map.of(
                        "prop-A",
                        new Value(false),
                        "prop-B",
                        new Value(
                            new MutableStructure(
                                Map.of("prop-C", new Value("str-val"), "prop-D", new Value(5.3)))),
                        "prop-E",
                        new Value(50),
                        "prop-F",
                        new Value(List.of(new Value("a"), new Value("b"))),
                        "prop-G",
                        new Value(
                            new MutableStructure(
                                Map.of(
                                    "prop-H", new Value(),
                                    "prop-I", new Value())))))));
  }

  @Test
  public void regularResolveWith2TargetingKeyShouldPrioritiseApiOverMap() {

    mockResolve(
        (ResolveFlagsRequest, streamObserver) -> {
          assertThat(ResolveFlagsRequest.getFlags(0)).isEqualTo("flags/flag");

          assertThat(ResolveFlagsRequest.getEvaluationContext())
              .isEqualTo(Structs.of("targeting_key", Values.of("my-targeting-key-1")));

          streamObserver.onNext(generateSampleResponse(Collections.emptyList()));
          streamObserver.onCompleted();
        });

    final FlagEvaluationDetails<Value> evaluationDetails =
        client.getObjectDetails("flag", DEFAULT_VALUE, SAMPLE_CONTEXT_2_TARGETING_KEYS);

    assertThat(evaluationDetails.getErrorCode()).isNull();
    assertThat(evaluationDetails.getVariant()).isEqualTo("flags/flag/variants/var-A");
    assertThat(evaluationDetails.getErrorMessage()).isBlank();
    assertThat(evaluationDetails.getValue())
        .isEqualTo(
            new Value(
                new MutableStructure(
                    Map.of(
                        "prop-A",
                        new Value(false),
                        "prop-B",
                        new Value(
                            new MutableStructure(
                                Map.of("prop-C", new Value("str-val"), "prop-D", new Value(5.3)))),
                        "prop-E",
                        new Value(50),
                        "prop-F",
                        new Value(List.of(new Value("a"), new Value("b"))),
                        "prop-G",
                        new Value(
                            new MutableStructure(
                                Map.of(
                                    "prop-H", new Value(),
                                    "prop-I", new Value())))))));
  }

  @Test
  public void regularResolveWithPath() {

    mockSampleResponse();

    // 1-element path to non-structure value
    FlagEvaluationDetails<Value> evaluationDetails =
        client.getObjectDetails("flag.prop-A", DEFAULT_VALUE, SAMPLE_CONTEXT);
    assertThat(evaluationDetails.getErrorCode()).isNull();
    assertThat(evaluationDetails.getVariant()).isEqualTo("flags/flag/variants/var-A");
    assertThat(evaluationDetails.getErrorMessage()).isBlank();
    assertThat(evaluationDetails.getValue()).isEqualTo(new Value(false));

    // 1-element path to structure
    evaluationDetails = client.getObjectDetails("flag.prop-B", DEFAULT_VALUE, SAMPLE_CONTEXT);
    assertThat(evaluationDetails.getErrorCode()).isNull();
    assertThat(evaluationDetails.getVariant()).isEqualTo("flags/flag/variants/var-A");
    assertThat(evaluationDetails.getErrorMessage()).isBlank();
    assertThat(evaluationDetails.getValue())
        .isEqualTo(
            new Value(
                new MutableStructure(
                    Map.of("prop-C", new Value("str-val"), "prop-D", new Value(5.3)))));

    // 2-element path to non-structure
    evaluationDetails =
        client.getObjectDetails("flag.prop-B.prop-C", DEFAULT_VALUE, SAMPLE_CONTEXT);
    assertThat(evaluationDetails.getErrorCode()).isNull();
    assertThat(evaluationDetails.getVariant()).isEqualTo("flags/flag/variants/var-A");
    assertThat(evaluationDetails.getErrorMessage()).isBlank();
    assertThat(evaluationDetails.getValue()).isEqualTo(new Value("str-val"));

    // 1-element path to null value, returns default
    evaluationDetails =
        client.getObjectDetails("flag.prop-G.prop-H", DEFAULT_VALUE, SAMPLE_CONTEXT);
    assertThat(evaluationDetails.getErrorCode()).isNull();
    assertThat(evaluationDetails.getVariant()).isEqualTo("flags/flag/variants/var-A");
    assertThat(evaluationDetails.getErrorMessage()).isBlank();
    assertThat(evaluationDetails.getValue()).isEqualTo(DEFAULT_VALUE);

    // derive field on non-structure
    evaluationDetails =
        client.getObjectDetails("flag.prop-A.not-exist", DEFAULT_VALUE, SAMPLE_CONTEXT);
    assertThat(evaluationDetails.getErrorCode()).isEqualTo(ErrorCode.TYPE_MISMATCH);
    assertThat(evaluationDetails.getVariant()).isBlank();
    assertThat(evaluationDetails.getReason()).isEqualTo("ERROR");
    assertThat(evaluationDetails.getErrorMessage())
        .isEqualTo(
            String.format(
                "Illegal attempt to derive field 'not-exist' on non-structure value '%s'",
                new Value(false)));
    assertThat(evaluationDetails.getValue()).isEqualTo(DEFAULT_VALUE);

    // non-existing field on structure
    evaluationDetails = client.getObjectDetails("flag.not-exist", DEFAULT_VALUE, SAMPLE_CONTEXT);
    assertThat(evaluationDetails.getErrorCode()).isEqualTo(ErrorCode.TYPE_MISMATCH);
    assertThat(evaluationDetails.getVariant()).isBlank();
    assertThat(evaluationDetails.getReason()).isEqualTo("ERROR");
    assertThat(evaluationDetails.getErrorMessage())
        .isEqualTo(
            String.format(
                "Illegal attempt to derive non-existing field 'not-exist' on structure value '%s'",
                new MutableStructure(
                    Map.of(
                        "prop-A",
                        new Value(false),
                        "prop-B",
                        new Value(
                            new MutableStructure(
                                Map.of("prop-C", new Value("str-val"), "prop-D", new Value(5.3)))),
                        "prop-E",
                        new Value(50),
                        "prop-F",
                        new Value(List.of(new Value("a"), new Value("b"))),
                        "prop-G",
                        new Value(
                            new MutableStructure(
                                Map.of(
                                    "prop-H", new Value(),
                                    "prop-I", new Value())))))));
    assertThat(evaluationDetails.getValue()).isEqualTo(DEFAULT_VALUE);

    // malformed path without flag name
    evaluationDetails = client.getObjectDetails("...", DEFAULT_VALUE, SAMPLE_CONTEXT);
    assertThat(evaluationDetails.getErrorCode()).isEqualTo(GENERAL);
    assertThat(evaluationDetails.getVariant()).isBlank();
    assertThat(evaluationDetails.getReason()).isEqualTo("ERROR");
    assertThat(evaluationDetails.getErrorMessage())
        .isEqualTo("com.spotify.confidence.Exceptions$IllegalValuePath: Illegal path string '...'");
    assertThat(evaluationDetails.getValue()).isEqualTo(DEFAULT_VALUE);
  }

  @Test
  public void booleanResolve() {
    mockSampleResponse();

    final FlagEvaluationDetails<Boolean> evaluationDetails =
        client.getBooleanDetails("flag.prop-A", true, SAMPLE_CONTEXT);

    assertThat(evaluationDetails.getValue()).isFalse();
    assertThat(evaluationDetails.getVariant()).isEqualTo("flags/flag/variants/var-A");
    assertThat(evaluationDetails.getErrorMessage()).isBlank();
    assertThat(evaluationDetails.getErrorCode()).isNull();
  }

  @Test
  public void stringResolve() {
    mockSampleResponse();

    final FlagEvaluationDetails<String> evaluationDetails =
        client.getStringDetails("flag.prop-B.prop-C", "default", SAMPLE_CONTEXT);

    assertThat(evaluationDetails.getValue()).isEqualTo("str-val");
    assertThat(evaluationDetails.getVariant()).isEqualTo("flags/flag/variants/var-A");
    assertThat(evaluationDetails.getErrorMessage()).isBlank();
    assertThat(evaluationDetails.getErrorCode()).isNull();
  }

  @Test
  public void integerResolve() {
    mockSampleResponse();

    final FlagEvaluationDetails<Integer> evaluationDetails =
        client.getIntegerDetails("flag.prop-E", 1000, SAMPLE_CONTEXT);

    assertThat(evaluationDetails.getValue()).isEqualTo(50);
    assertThat(evaluationDetails.getVariant()).isEqualTo("flags/flag/variants/var-A");
    assertThat(evaluationDetails.getErrorMessage()).isBlank();
    assertThat(evaluationDetails.getErrorCode()).isNull();
  }

  @Test
  public void doubleResolve() {
    mockSampleResponse();

    final FlagEvaluationDetails<Double> evaluationDetails =
        client.getDoubleDetails("flag.prop-B.prop-D", 10.5, SAMPLE_CONTEXT);

    assertThat(evaluationDetails.getValue()).isEqualTo(5.3);
    assertThat(evaluationDetails.getVariant()).isEqualTo("flags/flag/variants/var-A");
    assertThat(evaluationDetails.getErrorMessage()).isBlank();
    assertThat(evaluationDetails.getErrorCode()).isNull();
  }

  @Test
  public void longValueInIntegerSchemaResolveShouldFail() {
    mockSampleResponse(
        Collections.singletonList(
            new ValueSchemaHolder(
                "prop-X",
                Values.of(Integer.MAX_VALUE + 1L),
                FlagSchema.SchemaTypeCase.INT_SCHEMA)));

    final FlagEvaluationDetails<Integer> evaluationDetails =
        client.getIntegerDetails("flag.prop-X", 10, SAMPLE_CONTEXT);

    assertThat(evaluationDetails.getValue()).isEqualTo(10);
    assertThat(evaluationDetails.getVariant()).isNull();
    assertThat(evaluationDetails.getErrorMessage())
        .isEqualTo(
            "Mismatch between schema and value: value should be an int, but it is a double/long");
    assertThat(evaluationDetails.getErrorCode()).isEqualTo(ErrorCode.PARSE_ERROR);
  }

  @Test
  public void castingWithWrongType() {
    mockSampleResponse();

    final FlagEvaluationDetails<Boolean> evaluationDetails =
        client.getBooleanDetails("flag.prop-B.prop-C", true, SAMPLE_CONTEXT);
    assertThat(evaluationDetails.getValue()).isTrue();
    assertThat(evaluationDetails.getVariant()).isBlank();
    assertThat(evaluationDetails.getErrorMessage())
        .isEqualTo("Cannot cast value '%s' to expected type", new Value("str-val"));
    assertThat(evaluationDetails.getErrorCode()).isEqualTo(ErrorCode.TYPE_MISMATCH);
  }

  @Test
  public void shutdownShouldGiveStateNotReadyAndDefaultValues() {
    mockSampleResponse();
    openFeatureAPI.shutdown();

    final int defaultValue = 1000;
    final FlagEvaluationDetails<Integer> evaluationDetails =
        client.getIntegerDetails("flag.prop-E", defaultValue, SAMPLE_CONTEXT);

    final ProviderState state = openFeatureAPI.getProvider().getState();

    assertThat(state).isEqualTo(ProviderState.NOT_READY);
    assertThat(evaluationDetails.getValue()).isEqualTo(defaultValue);
  }

  @Test
  public void resolvesContainHeaderWithTelemetryData() {
    mockSampleResponse();

    client.getIntegerDetails("flag.prop-E", 1000, SAMPLE_CONTEXT);

    final Monitoring telemetrySnapshot = telemetry.peekSnapshot();
    final List<LibraryTraces> libraryTracesList = telemetrySnapshot.getLibraryTracesList();
    assertThat(libraryTracesList).hasSize(1);
    final LibraryTraces traces = libraryTracesList.get(0);
    assertThat(traces.getLibrary()).isEqualTo(LibraryTraces.Library.LIBRARY_OPEN_FEATURE);
    assertThat(traces.getTracesList()).hasSize(1);
    final LibraryTraces.Trace trace = traces.getTraces(0);
    assertThat(trace.getId()).isEqualTo(LibraryTraces.TraceId.TRACE_ID_RESOLVE_LATENCY);
    assertThat(trace.getMillisecondDuration()).isNotNegative();

    client.getIntegerDetails("flag.prop-Y", 1000, SAMPLE_CONTEXT);
  }

  //////
  // Utility
  //////

  private void mockResolve(
      BiConsumer<ResolveFlagsRequest, StreamObserver<ResolveFlagsResponse>> impl) {
    doAnswer(
            invocation -> {
              final ResolveFlagsRequest ResolveFlagsRequest = invocation.getArgument(0);
              final StreamObserver<ResolveFlagsResponse> streamObserver = invocation.getArgument(1);

              impl.accept(ResolveFlagsRequest, streamObserver);
              return null;
            })
        .when(serviceImpl)
        .resolveFlags(any(), any());
  }

  private void mockSampleResponse() {
    mockSampleResponse(Collections.emptyList());
  }

  private void mockSampleResponse(List<ValueSchemaHolder> additionalProps) {
    mockResolve(
        (resolveFlagRequest, streamObserver) -> {
          assertThat(resolveFlagRequest.getFlags(0)).isEqualTo("flags/flag");
          streamObserver.onNext(generateSampleResponse(additionalProps));
          streamObserver.onCompleted();
        });
  }
}
