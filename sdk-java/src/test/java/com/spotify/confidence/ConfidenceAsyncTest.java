package com.spotify.confidence;

import static org.junit.jupiter.api.Assertions.*;

import com.google.protobuf.Value;
import com.spotify.confidence.ConfidenceValue.Struct;
import com.spotify.confidence.shaded.flags.resolver.v1.ResolveFlagsResponse;
import com.spotify.confidence.shaded.flags.resolver.v1.ResolveReason;
import com.spotify.confidence.shaded.flags.resolver.v1.ResolvedFlag;
import com.spotify.confidence.shaded.flags.types.v1.FlagSchema;
import com.spotify.confidence.shaded.flags.types.v1.FlagSchema.StringFlagSchema;
import com.spotify.confidence.shaded.flags.types.v1.FlagSchema.StructFlagSchema;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ConfidenceAsyncTest {
  private final FakeEventSenderEngine fakeEngine = new FakeEventSenderEngine(new FakeClock());
  private final ResolverClientTestUtils.FakeFlagResolverClient fakeFlagResolverClient =
      new ResolverClientTestUtils.FakeFlagResolverClient();
  private static Confidence confidence;

  @BeforeEach
  void beforeEach() {
    confidence = Confidence.create(fakeEngine, fakeFlagResolverClient, "clientKey");
  }

  @AfterEach
  void afterEach() {
    // No-op
  }

  @Test
  void getValueFuture_basic() throws Exception {
    final Integer value = confidence.getValueFuture("flag.prop-E", 20).get(1, TimeUnit.SECONDS);
    assertEquals(50, value);

    final FlagEvaluation<Integer> evaluation =
        confidence.getEvaluationFuture("flag.prop-E", 20).get(1, TimeUnit.SECONDS);
    assertEquals(50, evaluation.getValue());
    assertEquals("flags/flag/variants/var-A", evaluation.getVariant());
    assertEquals("RESOLVE_REASON_MATCH", evaluation.getReason());
    assertTrue(evaluation.getErrorType().isEmpty());
    assertTrue(evaluation.getErrorMessage().isEmpty());
  }

  @Test
  void getValueFuture_fullStruct() throws Exception {
    final Struct value = confidence.getValueFuture("flag", Struct.EMPTY).get(1, TimeUnit.SECONDS);
    final Struct expected =
        Struct.builder()
            .set("prop-A", ConfidenceValue.of(false))
            .set(
                "prop-B",
                Struct.builder()
                    .set("prop-C", ConfidenceValue.of("str-val"))
                    .set("prop-D", ConfidenceValue.of(5.3))
                    .build())
            .set("prop-E", ConfidenceValue.of(50))
            .set(
                "prop-F",
                ConfidenceValue.List.of(List.of(ConfidenceValue.of("a"), ConfidenceValue.of("b"))))
            .set(
                "prop-G",
                Struct.builder()
                    .set("prop-H", ConfidenceValue.NULL_VALUE)
                    .set("prop-I", ConfidenceValue.NULL_VALUE)
                    .build())
            .build();
    assertEquals(expected, value);

    final FlagEvaluation<Struct> evaluation =
        confidence.getEvaluationFuture("flag", Struct.EMPTY).get(1, TimeUnit.SECONDS);
    assertEquals(expected, evaluation.getValue());
    assertEquals("flags/flag/variants/var-A", evaluation.getVariant());
    assertEquals("RESOLVE_REASON_MATCH", evaluation.getReason());
    assertTrue(evaluation.getErrorType().isEmpty());
    assertTrue(evaluation.getErrorMessage().isEmpty());
  }

  @Test
  void getValueFuture_incompatibleType() throws Exception {
    final FlagEvaluation<String> evaluation =
        confidence.getEvaluationFuture("flag.prop-E", "test").get(1, TimeUnit.SECONDS);
    assertEquals("test", evaluation.getValue());
    assertEquals("", evaluation.getVariant());
    assertEquals("ERROR", evaluation.getReason());
    assertEquals(ErrorType.INVALID_VALUE_TYPE, evaluation.getErrorType().get());
    assertEquals(
        "Default type class java.lang.String, but value of "
            + "type class com.spotify.confidence.ConfidenceValue$Integer",
        evaluation.getErrorMessage().get());
  }

  @Test
  void getValueFuture_unsupportedType() throws Exception {
    final java.util.Date dateObject = java.util.Date.from(Instant.ofEpochSecond(100));
    final FlagEvaluation<java.util.Date> evaluation =
        confidence.getEvaluationFuture("flag.prop-E", dateObject).get(1, TimeUnit.SECONDS);
    assertEquals(dateObject, evaluation.getValue());
    assertEquals("", evaluation.getVariant());
    assertEquals("ERROR", evaluation.getReason());
    assertEquals(ErrorType.INVALID_VALUE_TYPE, evaluation.getErrorType().get());
    assertEquals("Illegal value type: class java.util.Date", evaluation.getErrorMessage().get());
  }

  @Test
  void getValueFuture_nullValue() throws Exception {
    final FlagEvaluation<String> evaluation =
        confidence.getEvaluationFuture("flag.prop-G.prop-H", "test").get(1, TimeUnit.SECONDS);
    assertEquals("test", evaluation.getValue());
    assertEquals("flags/flag/variants/var-A", evaluation.getVariant());
    assertEquals("RESOLVE_REASON_MATCH", evaluation.getReason());
    assertTrue(evaluation.getErrorType().isEmpty());
    assertTrue(evaluation.getErrorMessage().isEmpty());
  }

  @Test
  void getValueFuture_unexpectedReturnedFlag() throws Exception {
    final Integer value =
        confidence.getValueFuture("wrong-flag.prop-E", 20).get(1, TimeUnit.SECONDS);
    assertEquals(20, value);

    final FlagEvaluation<Integer> evaluation =
        confidence.getEvaluationFuture("wrong-flag.prop-E", 20).get(1, TimeUnit.SECONDS);
    assertEquals(20, evaluation.getValue());
    assertEquals("", evaluation.getVariant());
    assertEquals("ERROR", evaluation.getReason());
    assertEquals(ErrorType.INTERNAL_ERROR, evaluation.getErrorType().get());
    assertEquals("Unexpected flag 'flag' from remote", evaluation.getErrorMessage().get());
  }

  @Test
  void getValueFuture_invalidValuePath() throws Exception {
    final Integer value = confidence.getValueFuture("flag.prop-X", 20).get(1, TimeUnit.SECONDS);
    assertEquals(20, value);

    final FlagEvaluation<Integer> evaluation =
        confidence.getEvaluationFuture("flag.prop-X", 20).get(1, TimeUnit.SECONDS);
    assertEquals(20, evaluation.getValue());
    assertEquals("", evaluation.getVariant());
    assertEquals("ERROR", evaluation.getReason());
    assertEquals(ErrorType.INVALID_VALUE_PATH, evaluation.getErrorType().get());
    assertTrue(
        evaluation
            .getErrorMessage()
            .get()
            .startsWith(
                "Illegal attempt to derive non-existing field 'prop-X' on structure value"));
  }

  @Test
  void getValueFuture_malformedValuePath() throws Exception {
    final Integer value = confidence.getValueFuture("...", 20).get(1, TimeUnit.SECONDS);
    assertEquals(20, value);

    final FlagEvaluation<Integer> evaluation =
        confidence.getEvaluationFuture("...", 20).get(1, TimeUnit.SECONDS);
    assertEquals(20, evaluation.getValue());
    assertEquals("", evaluation.getVariant());
    assertEquals("ERROR", evaluation.getReason());
    assertEquals(ErrorType.INVALID_VALUE_PATH, evaluation.getErrorType().get());
    assertTrue(evaluation.getErrorMessage().get().startsWith("Illegal path string '...'"));
  }

  @Test
  void getValueFuture_flagNotFound() throws Exception {
    fakeFlagResolverClient.response = ResolveFlagsResponse.newBuilder().getDefaultInstanceForType();
    final Integer value = confidence.getValueFuture("unknown-flag", 20).get(1, TimeUnit.SECONDS);
    assertEquals(20, value);

    final FlagEvaluation<Integer> evaluation =
        confidence.getEvaluationFuture("unknown-flag", 20).get(1, TimeUnit.SECONDS);
    assertEquals(20, evaluation.getValue());
    assertEquals("", evaluation.getVariant());
    assertEquals("ERROR", evaluation.getReason());
    assertEquals(ErrorType.FLAG_NOT_FOUND, evaluation.getErrorType().get());
    assertTrue(
        evaluation.getErrorMessage().get().startsWith("No active flag 'unknown-flag' was found"));
  }

  @Test
  void getValueFuture_noSegmentMatch() throws Exception {
    fakeFlagResolverClient.response =
        ResolveFlagsResponse.newBuilder()
            .addResolvedFlags(
                ResolvedFlag.newBuilder()
                    .setFlag("flags/no-match-flag")
                    .setVariant("")
                    .setReason(ResolveReason.RESOLVE_REASON_NO_SEGMENT_MATCH)
                    .build())
            .build();
    final Integer value = confidence.getValueFuture("no-match-flag", 20).get(1, TimeUnit.SECONDS);
    assertEquals(20, value);

    final FlagEvaluation<Integer> evaluation =
        confidence.getEvaluationFuture("no-match-flag", 20).get(1, TimeUnit.SECONDS);
    assertEquals(20, evaluation.getValue());
    assertEquals("", evaluation.getVariant());
    assertEquals("RESOLVE_REASON_NO_SEGMENT_MATCH", evaluation.getReason());
    assertTrue(evaluation.getErrorType().isEmpty());
    assertTrue(evaluation.getErrorMessage().isEmpty());
  }

  @Test
  void getValueFuture_flagWithErroneousSchema() throws Exception {
    fakeFlagResolverClient.response =
        ResolveFlagsResponse.newBuilder()
            .addResolvedFlags(
                ResolvedFlag.newBuilder()
                    .setFlagSchema(
                        StructFlagSchema.newBuilder()
                            .putSchema(
                                "key",
                                FlagSchema.newBuilder()
                                    .setStringSchema(StringFlagSchema.getDefaultInstance())
                                    .build()))
                    .setValue(
                        com.google.protobuf.Struct.newBuilder()
                            .putAllFields(
                                Map.of("key", Value.newBuilder().setNumberValue(3.14).build()))
                            .build())
                    .setFlag("flags/wrong-schema-flag")
                    .setVariant("testB")
                    .setReason(ResolveReason.RESOLVE_REASON_MATCH)
                    .build())
            .build();
    final Integer value =
        confidence.getValueFuture("wrong-schema-flag", 20).get(1, TimeUnit.SECONDS);
    assertEquals(20, value);

    final FlagEvaluation<Integer> evaluation =
        confidence.getEvaluationFuture("wrong-schema-flag", 20).get(1, TimeUnit.SECONDS);
    assertEquals(20, evaluation.getValue());
    assertEquals("", evaluation.getVariant());
    assertEquals("ERROR", evaluation.getReason());
    assertEquals(ErrorType.INTERNAL_ERROR, evaluation.getErrorType().get());
    assertTrue(
        evaluation
            .getErrorMessage()
            .get()
            .startsWith(
                "Mismatch between schema and value: number_value: 3.14\n"
                    + " is a Number, but it should be STRING_SCHEMA"));
  }

  @Test
  void getValueFuture_internalError() throws Exception {
    final Confidence confidence =
        Confidence.create(fakeEngine, new ConfidenceTest.FailingFlagResolverClient(), "clientKey");
    final Integer value = confidence.getValueFuture("no-match-flag", 20).get(1, TimeUnit.SECONDS);
    assertEquals(20, value);

    final FlagEvaluation<Integer> evaluation =
        confidence.getEvaluationFuture("no-match-flag", 20).get(1, TimeUnit.SECONDS);
    assertEquals(20, evaluation.getValue());
    assertEquals("", evaluation.getVariant());
    assertEquals("ERROR", evaluation.getReason());
    assertEquals(ErrorType.INTERNAL_ERROR, evaluation.getErrorType().get());
    assertTrue(
        evaluation.getErrorMessage().get().startsWith("Crashing while performing network call"));
    // Also assert that the future completed normally (not exceptionally)
    final CompletableFuture<FlagEvaluation<Integer>> future =
        confidence.getEvaluationFuture("no-match-flag", 20);
    assertTrue(future.isDone());
    assertFalse(future.isCompletedExceptionally());
  }
}
