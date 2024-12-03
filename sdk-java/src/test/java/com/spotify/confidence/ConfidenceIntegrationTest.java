package com.spotify.confidence;

import static com.spotify.confidence.ErrorType.*;
import static com.spotify.confidence.ResolverClientTestUtils.generateSampleResponse;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import com.google.protobuf.util.Structs;
import com.google.protobuf.util.Values;
import com.spotify.confidence.ResolverClientTestUtils.ValueSchemaHolder;
import com.spotify.confidence.shaded.flags.resolver.v1.FlagResolverServiceGrpc.FlagResolverServiceImplBase;
import com.spotify.confidence.shaded.flags.resolver.v1.ResolveFlagsRequest;
import com.spotify.confidence.shaded.flags.resolver.v1.ResolveFlagsResponse;
import com.spotify.confidence.shaded.flags.resolver.v1.ResolvedFlag;
import com.spotify.confidence.shaded.flags.types.v1.FlagSchema;
import com.spotify.confidence.telemetry.FakeTelemetryClientInterceptor;
import com.spotify.telemetry.v1.LibraryTraces;
import com.spotify.telemetry.v1.Monitoring;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
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

final class ConfidenceIntegrationTest {
  private static Server server;
  private static ManagedChannel channel;

  private static final FlagResolverServiceImplBase serviceImpl =
      mock(FlagResolverServiceImplBase.class);

  private static final Map<String, ConfidenceValue> SAMPLE_CONTEXT_WITHOUT_TARGETING_KEY =
      Map.of("my-key", ConfidenceValue.of(true));

  private static final String DEFAULT_VALUE = "string-default";

  private static final Map<String, ConfidenceValue> SAMPLE_CONTEXT =
      Map.of(
          "my-targeting-key",
          ConfidenceValue.of("the-target-id"),
          "my-key",
          ConfidenceValue.of(true));

  static final String serverName = InProcessServerBuilder.generateName();
  private Telemetry telemetry;
  private Confidence confidence;
  private FakeTelemetryClientInterceptor telemetryInterceptor;

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
    telemetryInterceptor = new FakeTelemetryClientInterceptor(telemetry);
    final FlagResolverClientImpl flagResolver =
        new FlagResolverClientImpl(
            new GrpcFlagResolver("fake-secret", channel, telemetryInterceptor), telemetry);
    confidence = Confidence.create(fakeEventSender, flagResolver, "");
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

    final FlagEvaluation<String> evaluationDetails =
        confidence.withContext(SAMPLE_CONTEXT).getEvaluation("not-existing", DEFAULT_VALUE);

    assertThat(evaluationDetails.getValue()).isEqualTo(DEFAULT_VALUE);
    assertThat(evaluationDetails.getErrorType().get()).isEqualTo(FLAG_NOT_FOUND);
    assertThat(evaluationDetails.getReason()).isEqualTo("ERROR");
    assertThat(evaluationDetails.getVariant()).isEmpty();
    assertThat(evaluationDetails.getErrorMessage().get())
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

    final FlagEvaluation<String> evaluationDetails =
        confidence.withContext(SAMPLE_CONTEXT).getEvaluation("flag", DEFAULT_VALUE);

    assertThat(evaluationDetails.getValue()).isEqualTo(DEFAULT_VALUE);
    assertThat(evaluationDetails.getErrorType().get()).isEqualTo(INTERNAL_ERROR);
    assertThat(evaluationDetails.getReason()).isEqualTo("ERROR");
    assertThat(evaluationDetails.getVariant()).isEmpty();
    assertThat(evaluationDetails.getErrorMessage().get())
        .isEqualTo("Unexpected flag 'unexpected-flag' from remote");
  }

  @Test
  public void unavailableApi() {

    mockResolve(
        (request, streamObserver) -> streamObserver.onError(Status.UNAVAILABLE.asException()));

    final FlagEvaluation<String> evaluationDetails =
        confidence.withContext(SAMPLE_CONTEXT).getEvaluation("flags/whatever", DEFAULT_VALUE);

    assertThat(evaluationDetails.getValue()).isEqualTo(DEFAULT_VALUE);
    assertThat(evaluationDetails.getErrorType().get()).isEqualTo(ErrorType.NETWORK_ERROR);
    assertThat(evaluationDetails.getReason()).isEqualTo("ERROR");
    assertThat(evaluationDetails.getErrorMessage().get())
        .isEqualTo("io.grpc.StatusRuntimeException: UNAVAILABLE");
    assertThat(evaluationDetails.getVariant()).isEmpty();
  }

  @Test
  public void unauthenticated() {

    mockResolve(
        (request, streamObserver) -> streamObserver.onError(Status.UNAUTHENTICATED.asException()));
    final FlagEvaluation<String> evaluationDetails =
        confidence.withContext(SAMPLE_CONTEXT).getEvaluation("flags/whatever", DEFAULT_VALUE);

    assertThat(evaluationDetails.getValue()).isEqualTo(DEFAULT_VALUE);
    assertThat(evaluationDetails.getErrorType().get()).isEqualTo(NETWORK_ERROR);
    assertThat(evaluationDetails.getReason()).isEqualTo("ERROR");
    assertThat(evaluationDetails.getErrorMessage().get())
        .isEqualTo("io.grpc.StatusRuntimeException: UNAUTHENTICATED");
    assertThat(evaluationDetails.getVariant()).isEmpty();
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

    final FlagEvaluation<String> evaluationDetails =
        confidence.withContext(SAMPLE_CONTEXT).getEvaluation("whatever", DEFAULT_VALUE);

    assertThat(evaluationDetails.getValue()).isEqualTo(DEFAULT_VALUE);
    assertThat(evaluationDetails.getErrorType()).isEmpty();
    assertThat(evaluationDetails.getErrorMessage()).isEmpty();
    assertThat(evaluationDetails.getReason()).startsWith("RESOLVE_REASON_UNSPECIFIED");
    assertThat(evaluationDetails.getVariant()).isEmpty();
  }

  @Test
  public void regularResolve() {

    mockResolve(
        (ResolveFlagsRequest, streamObserver) -> {
          assertThat(ResolveFlagsRequest.getFlags(0)).isEqualTo("flags/flag");

          assertThat(ResolveFlagsRequest.getEvaluationContext())
              .isEqualTo(
                  Structs.of(
                      "my-targeting-key", Values.of("the-target-id"), "my-key", Values.of(true)));

          streamObserver.onNext(generateSampleResponse(Collections.emptyList()));
          streamObserver.onCompleted();
        });

    final FlagEvaluation<ConfidenceValue.Struct> evaluationDetails =
        confidence.withContext(SAMPLE_CONTEXT).getEvaluation("flag", ConfidenceValue.Struct.EMPTY);

    assertThat(evaluationDetails.getErrorType()).isEmpty();
    assertThat(evaluationDetails.getVariant()).isEqualTo("flags/flag/variants/var-A");
    assertThat(evaluationDetails.getErrorMessage()).isEmpty();
    // TODO fixup!
    assertThat(evaluationDetails.getValue())
        .isEqualTo(
            ConfidenceValue.of(
                Map.of(
                    "prop-A",
                    ConfidenceValue.of(false),
                    "prop-B",
                    ConfidenceValue.of(
                        Map.of(
                            "prop-C",
                            ConfidenceValue.of("str-val"),
                            "prop-D",
                            ConfidenceValue.of(5.3))),
                    "prop-E",
                    ConfidenceValue.of(50),
                    "prop-F",
                    ConfidenceValue.of(List.of(ConfidenceValue.of("a"), ConfidenceValue.of("b"))),
                    "prop-G",
                    ConfidenceValue.of(
                        Map.of(
                            "prop-H", ConfidenceValue.NULL_VALUE,
                            "prop-I", ConfidenceValue.NULL_VALUE)))));
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

    final FlagEvaluation<ConfidenceValue> evaluationDetails =
        confidence
            .withContext(SAMPLE_CONTEXT_WITHOUT_TARGETING_KEY)
            .getEvaluation("flag", ConfidenceValue.Struct.EMPTY);

    assertThat(evaluationDetails.getErrorType()).isEmpty();
    assertThat(evaluationDetails.getVariant()).isEqualTo("flags/flag/variants/var-A");
    assertThat(evaluationDetails.getErrorMessage()).isEmpty();
    assertThat(evaluationDetails.getValue())
        .isEqualTo(
            ConfidenceValue.of(
                Map.of(
                    "prop-A",
                    ConfidenceValue.of(false),
                    "prop-B",
                    ConfidenceValue.of(
                        Map.of(
                            "prop-C",
                            ConfidenceValue.of("str-val"),
                            "prop-D",
                            ConfidenceValue.of(5.3))),
                    "prop-E",
                    ConfidenceValue.of(50),
                    "prop-F",
                    ConfidenceValue.of(List.of(ConfidenceValue.of("a"), ConfidenceValue.of("b"))),
                    "prop-G",
                    ConfidenceValue.of(
                        Map.of(
                            "prop-H", ConfidenceValue.NULL_VALUE,
                            "prop-I", ConfidenceValue.NULL_VALUE)))));
  }

  @Test
  public void regularResolveWithPath() {

    mockSampleResponse();

    // 1-element path to non-structure value
    final FlagEvaluation<Boolean> evaluationDetails =
        confidence.withContext(SAMPLE_CONTEXT).getEvaluation("flag.prop-A", false);

    assertThat(evaluationDetails.getErrorType()).isEmpty();
    assertThat(evaluationDetails.getVariant()).isEqualTo("flags/flag/variants/var-A");
    assertThat(evaluationDetails.getErrorMessage()).isEmpty();
    assertThat(evaluationDetails.getValue().booleanValue()).isEqualTo(false);

    // 1-element path to structure
    final FlagEvaluation<ConfidenceValue.Struct> evaluationDetails2 =
        confidence
            .withContext(SAMPLE_CONTEXT)
            .getEvaluation("flag.prop-B", ConfidenceValue.Struct.EMPTY);
    assertThat(evaluationDetails2.getErrorType()).isEmpty();
    assertThat(evaluationDetails2.getVariant()).isEqualTo("flags/flag/variants/var-A");
    assertThat(evaluationDetails2.getErrorMessage()).isEmpty();
    assertThat(evaluationDetails2.getValue())
        .isEqualTo(
            ConfidenceValue.of(
                Map.of(
                    "prop-C", ConfidenceValue.of("str-val"), "prop-D", ConfidenceValue.of(5.3))));

    // 2-element path to non-structure
    final FlagEvaluation<String> evaluationDetails3 =
        confidence.withContext(SAMPLE_CONTEXT).getEvaluation("flag.prop-B.prop-C", DEFAULT_VALUE);
    assertThat(evaluationDetails3.getErrorType()).isEmpty();
    assertThat(evaluationDetails3.getVariant()).isEqualTo("flags/flag/variants/var-A");
    assertThat(evaluationDetails3.getErrorMessage()).isEmpty();
    assertThat(evaluationDetails3.getValue()).isEqualTo("str-val");

    // 1-element path to null value, returns default
    final FlagEvaluation<String> evaluationDetails4 =
        confidence.withContext(SAMPLE_CONTEXT).getEvaluation("flag.prop-G.prop-H", DEFAULT_VALUE);
    assertThat(evaluationDetails4.getErrorType()).isEmpty();
    assertThat(evaluationDetails4.getVariant()).isEqualTo("flags/flag/variants/var-A");
    assertThat(evaluationDetails4.getErrorMessage()).isEmpty();
    assertThat(evaluationDetails4.getValue()).isEqualTo(DEFAULT_VALUE);

    // derive field on non-structure
    final FlagEvaluation<String> evaluationDetails5 =
        confidence
            .withContext(SAMPLE_CONTEXT)
            .getEvaluation("flag.prop-A.not-exist", DEFAULT_VALUE);
    assertThat(evaluationDetails5.getErrorType().get()).isEqualTo(ErrorType.INTERNAL_ERROR);
    assertThat(evaluationDetails5.getVariant()).isEmpty();
    assertThat(evaluationDetails5.getReason()).isEqualTo("ERROR");
    assertThat(evaluationDetails5.getErrorMessage().get()).isEqualTo("Not a StructValue");
    assertThat(evaluationDetails5.getValue()).isEqualTo(DEFAULT_VALUE);

    // non-existing field on structure
    final FlagEvaluation<String> evaluationDetails6 =
        confidence.withContext(SAMPLE_CONTEXT).getEvaluation("flag.not-exist", DEFAULT_VALUE);
    assertThat(evaluationDetails6.getErrorType().get()).isEqualTo(ErrorType.INVALID_VALUE_PATH);
    assertThat(evaluationDetails6.getVariant()).isEmpty();
    assertThat(evaluationDetails6.getReason()).isEqualTo("ERROR");
    assertThat(evaluationDetails6.getErrorMessage().get())
        .isEqualTo(
            "Illegal attempt to derive non-existing field 'not-exist' on structure value "
                + "'{prop-B={prop-C=str-val, prop-D=5.3}, prop-E=50, prop-G={prop-I=NULL, prop-H=NULL}, "
                + "prop-F=[[a, b]], prop-A=false}'");
    assertThat(evaluationDetails6.getValue()).isEqualTo(DEFAULT_VALUE);

    // malformed path without flag name
    final FlagEvaluation<String> evaluationDetails7 =
        confidence.withContext(SAMPLE_CONTEXT).getEvaluation("...", DEFAULT_VALUE);
    assertThat(evaluationDetails7.getErrorType().get()).isEqualTo(ErrorType.INVALID_VALUE_PATH);
    assertThat(evaluationDetails7.getVariant()).isEmpty();
    assertThat(evaluationDetails7.getReason()).isEqualTo("ERROR");
    assertThat(evaluationDetails7.getErrorMessage().get()).isEqualTo("Illegal path string '...'");
    assertThat(evaluationDetails7.getValue()).isEqualTo(DEFAULT_VALUE);
  }

  @Test
  public void booleanResolve() {
    mockSampleResponse();

    final FlagEvaluation<Boolean> evaluationDetails =
        confidence.withContext(SAMPLE_CONTEXT).getEvaluation("flag.prop-A", true);

    assertThat(evaluationDetails.getValue()).isFalse();
    assertThat(evaluationDetails.getVariant()).isEqualTo("flags/flag/variants/var-A");
    assertThat(evaluationDetails.getErrorMessage()).isEmpty();
    assertThat(evaluationDetails.getErrorType()).isEmpty();
  }

  @Test
  public void stringResolve() {
    mockSampleResponse();

    final FlagEvaluation<String> evaluationDetails =
        confidence.withContext(SAMPLE_CONTEXT).getEvaluation("flag.prop-B.prop-C", "default");

    assertThat(evaluationDetails.getValue()).isEqualTo("str-val");
    assertThat(evaluationDetails.getVariant()).isEqualTo("flags/flag/variants/var-A");
    assertThat(evaluationDetails.getErrorMessage()).isEmpty();
    assertThat(evaluationDetails.getErrorType()).isEmpty();
  }

  @Test
  public void integerResolve() {
    mockSampleResponse();

    final FlagEvaluation<Integer> evaluationDetails =
        confidence.withContext(SAMPLE_CONTEXT).getEvaluation("flag.prop-E", 1000);

    assertThat(evaluationDetails.getValue()).isEqualTo(50);
    assertThat(evaluationDetails.getVariant()).isEqualTo("flags/flag/variants/var-A");
    assertThat(evaluationDetails.getErrorMessage()).isEmpty();
    assertThat(evaluationDetails.getErrorType()).isEmpty();
  }

  @Test
  public void doubleResolve() {
    mockSampleResponse();

    final FlagEvaluation<Double> evaluationDetails =
        confidence.withContext(SAMPLE_CONTEXT).getEvaluation("flag.prop-B.prop-D", 10.5);

    assertThat(evaluationDetails.getValue()).isEqualTo(5.3);
    assertThat(evaluationDetails.getVariant()).isEqualTo("flags/flag/variants/var-A");
    assertThat(evaluationDetails.getErrorMessage()).isEmpty();
    assertThat(evaluationDetails.getErrorType()).isEmpty();
  }

  @Test
  public void longValueInIntegerSchemaResolveShouldFail() {
    mockSampleResponse(
        Collections.singletonList(
            new ValueSchemaHolder(
                "prop-X",
                Values.of(Integer.MAX_VALUE + 1L),
                FlagSchema.SchemaTypeCase.INT_SCHEMA)));

    final FlagEvaluation<Integer> evaluationDetails =
        confidence.withContext(SAMPLE_CONTEXT).getEvaluation("flag.prop-X", 10);

    assertThat(evaluationDetails.getValue()).isEqualTo(10);
    assertThat(evaluationDetails.getVariant()).isEmpty();
    assertThat(evaluationDetails.getErrorMessage().get())
        .isEqualTo(
            "Mismatch between schema and value: 2.147483648E9 should be an int, but it is a double/long");
    assertThat(evaluationDetails.getErrorType().get()).isEqualTo(ErrorType.INTERNAL_ERROR);
  }

  @Test
  public void castingWithWrongType() {
    mockSampleResponse();

    final FlagEvaluation<Boolean> evaluationDetails =
        confidence.withContext(SAMPLE_CONTEXT).getEvaluation("flag.prop-B.prop-C", true);
    assertThat(evaluationDetails.getValue()).isTrue();
    assertThat(evaluationDetails.getVariant()).isEmpty();
    assertThat(evaluationDetails.getErrorMessage().get())
        .isEqualTo(
            "Default type class java.lang.Boolean, but value of type class "
                + "com.spotify.confidence.ConfidenceValue$StringValue");
    assertThat(evaluationDetails.getErrorType().get()).isEqualTo(ErrorType.INVALID_VALUE_TYPE);
  }

  @Test
  public void resolvesContainHeaderWithTelemetryData() {
    mockSampleResponse();

    confidence.withContext(SAMPLE_CONTEXT).getEvaluation("flag.prop-E", 1000);

    final Monitoring telemetrySnapshot = telemetry.getSnapshotInternal();
    final List<LibraryTraces> libraryTracesList = telemetrySnapshot.getLibraryTracesList();
    assertThat(libraryTracesList).hasSize(1);
    final LibraryTraces traces = libraryTracesList.get(0);
    assertThat(traces.getLibrary()).isEqualTo(LibraryTraces.Library.LIBRARY_CONFIDENCE);
    assertThat(traces.getTracesList()).hasSize(1);
    final LibraryTraces.Trace trace = traces.getTraces(0);
    assertThat(trace.getId()).isEqualTo(LibraryTraces.TraceId.TRACE_ID_RESOLVE_LATENCY);
    assertThat(trace.getMillisecondDuration()).isNotNegative();

    confidence.withContext(SAMPLE_CONTEXT).getEvaluation("flag.prop-Y", 1000);

    // verify that the grpc request contains telemetry data in the header
    final Metadata storedHeaders = telemetryInterceptor.getStoredHeaders();
    assertThat(storedHeaders.containsKey(TelemetryClientInterceptor.HEADER_KEY)).isTrue();
  }

  @Test
  public void resolveDoesNotContainHeaderWithTelemetryDataWhenDisabled() {
    final FakeEventSenderEngine fakeEventSender = new FakeEventSenderEngine(new FakeClock());
    channel = InProcessChannelBuilder.forName(serverName).directExecutor().build();
    // telemetry is null
    final FakeTelemetryClientInterceptor nullTelemetryInterceptor =
        new FakeTelemetryClientInterceptor(null);
    final FlagResolverClientImpl flagResolver =
        new FlagResolverClientImpl(
            new GrpcFlagResolver("fake-secret", channel, nullTelemetryInterceptor));
    confidence = Confidence.create(fakeEventSender, flagResolver, "clientKey");

    mockSampleResponse();

    confidence.withContext(SAMPLE_CONTEXT).getEvaluation("flag.prop-E", 1000);
    confidence.withContext(SAMPLE_CONTEXT).getEvaluation("flag.prop-E", 1000);

    // verify that the grpc request does not contain telemetry data in the header
    final Metadata storedHeaders = nullTelemetryInterceptor.getStoredHeaders();
    assertThat(storedHeaders.containsKey(TelemetryClientInterceptor.HEADER_KEY)).isFalse();
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
