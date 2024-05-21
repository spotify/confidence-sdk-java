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
import java.io.IOException;
import java.sql.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

public class ConfidenceTest {
  private final FakeEventSenderEngine fakeEngine = new FakeEventSenderEngine(new FakeClock());
  private final ResolverClientTestUtils.FakeFlagResolverClient fakeFlagResolverClient =
      new ResolverClientTestUtils.FakeFlagResolverClient();

  @Test
  void getValue() {
    final Confidence confidence = Confidence.create(fakeEngine, fakeFlagResolverClient);
    final Integer value = confidence.getValue("flag.prop-E", 20);
    assertEquals(50, value);

    final FlagEvaluation<Integer> evaluation = confidence.getEvaluation("flag.prop-E", 20);

    assertEquals(50, evaluation.getValue());
    assertEquals("flags/flag/variants/var-A", evaluation.getVariant());
    assertEquals("RESOLVE_REASON_MATCH", evaluation.getReason());
    assertTrue(evaluation.getErrorType().isEmpty());
    assertTrue(evaluation.getErrorMessage().isEmpty());
  }

  @Test
  void getFullValue() {
    final Confidence confidence = Confidence.create(fakeEngine, fakeFlagResolverClient);
    final Struct value = confidence.getValue("flag", Struct.EMPTY);
    // This mocked struct is defined in ResolverClientTestUtils
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

    final FlagEvaluation<Struct> evaluation = confidence.getEvaluation("flag", Struct.EMPTY);

    assertEquals(expected, evaluation.getValue());
    assertEquals("flags/flag/variants/var-A", evaluation.getVariant());
    assertEquals("RESOLVE_REASON_MATCH", evaluation.getReason());
    assertTrue(evaluation.getErrorType().isEmpty());
    assertTrue(evaluation.getErrorMessage().isEmpty());
  }

  @Test
  void getValueIncompatibleType() {
    final Confidence confidence = Confidence.create(fakeEngine, fakeFlagResolverClient);
    final FlagEvaluation<String> evaluation = confidence.getEvaluation("flag.prop-E", "test");
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
  void getValueUnsupportedType() {
    final Confidence confidence = Confidence.create(fakeEngine, fakeFlagResolverClient);
    final FlagEvaluation<Date> evaluation =
        confidence.getEvaluation("flag.prop-E", Date.valueOf("2024-4-2"));
    assertEquals(Date.valueOf("2024-4-2"), evaluation.getValue());
    assertEquals("", evaluation.getVariant());
    assertEquals("ERROR", evaluation.getReason());
    assertEquals(ErrorType.INVALID_VALUE_TYPE, evaluation.getErrorType().get());
    assertEquals("Illegal value type: class java.sql.Date", evaluation.getErrorMessage().get());
  }

  @Test
  void getNullValue() {
    final Confidence confidence = Confidence.create(fakeEngine, fakeFlagResolverClient);
    final FlagEvaluation<String> evaluation = confidence.getEvaluation("flag.prop-G.prop-H", "test");
    assertEquals("test", evaluation.getValue());
    assertEquals("flags/flag/variants/var-A", evaluation.getVariant());
    assertEquals("RESOLVE_REASON_MATCH", evaluation.getReason());
    assertTrue(evaluation.getErrorType().isEmpty());
    assertTrue(evaluation.getErrorMessage().isEmpty());
  }

  @Test
  void unexpectedReturnedFlag() {
    final Confidence confidence = Confidence.create(fakeEngine, fakeFlagResolverClient);
    final Integer value = confidence.getValue("wrong-flag.prop-E", 20);
    assertEquals(20, value);

    final FlagEvaluation<Integer> evaluation = confidence.getEvaluation("wrong-flag.prop-E", 20);

    assertEquals(20, evaluation.getValue());
    assertEquals("", evaluation.getVariant());
    assertEquals("ERROR", evaluation.getReason());
    assertEquals(ErrorType.INTERNAL_ERROR, evaluation.getErrorType().get());
    assertEquals("Unexpected flag 'flag' from remote", evaluation.getErrorMessage().get());
  }

  @Test
  void invalidValuePath() {
    final Confidence confidence = Confidence.create(fakeEngine, fakeFlagResolverClient);
    final Integer value = confidence.getValue("flag.prop-X", 20);
    assertEquals(20, value);

    final FlagEvaluation<Integer> evaluation = confidence.getEvaluation("flag.prop-X", 20);

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
  void malformedValuePath() {
    final Confidence confidence = Confidence.create(fakeEngine, fakeFlagResolverClient);
    final Integer value = confidence.getValue("...", 20);
    assertEquals(20, value);

    final FlagEvaluation<Integer> evaluation = confidence.getEvaluation("...", 20);

    assertEquals(20, evaluation.getValue());
    assertEquals("", evaluation.getVariant());
    assertEquals("ERROR", evaluation.getReason());
    assertEquals(ErrorType.INVALID_VALUE_PATH, evaluation.getErrorType().get());
    assertTrue(evaluation.getErrorMessage().get().startsWith("Illegal path string '...'"));
  }

  @Test
  void flagNotFound() {
    fakeFlagResolverClient.response = ResolveFlagsResponse.newBuilder().getDefaultInstanceForType();
    final Confidence confidence = Confidence.create(fakeEngine, fakeFlagResolverClient);
    final Integer value = confidence.getValue("unknown-flag", 20);
    assertEquals(20, value);

    final FlagEvaluation<Integer> evaluation = confidence.getEvaluation("unknown-flag", 20);

    assertEquals(20, evaluation.getValue());
    assertEquals("", evaluation.getVariant());
    assertEquals("ERROR", evaluation.getReason());
    assertEquals(ErrorType.FLAG_NOT_FOUND, evaluation.getErrorType().get());
    assertTrue(
        evaluation.getErrorMessage().get().startsWith("No active flag 'unknown-flag' was found"));
  }

  @Test
  void noSegmentMatch() {
    fakeFlagResolverClient.response =
        ResolveFlagsResponse.newBuilder()
            .addResolvedFlags(
                ResolvedFlag.newBuilder()
                    .setFlag("flags/no-match-flag")
                    .setVariant("")
                    .setReason(ResolveReason.RESOLVE_REASON_NO_SEGMENT_MATCH)
                    .build())
            .build();
    final Confidence confidence = Confidence.create(fakeEngine, fakeFlagResolverClient);
    final Integer value = confidence.getValue("no-match-flag", 20);
    assertEquals(20, value);

    final FlagEvaluation<Integer> evaluation = confidence.getEvaluation("no-match-flag", 20);

    assertEquals(20, evaluation.getValue());
    assertEquals("", evaluation.getVariant());
    assertEquals("RESOLVE_REASON_NO_SEGMENT_MATCH", evaluation.getReason());
    assertTrue(evaluation.getErrorType().isEmpty());
    assertTrue(evaluation.getErrorMessage().isEmpty());
  }

  @Test
  void flagWithErroneousSchema() {
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
    final Confidence confidence = Confidence.create(fakeEngine, fakeFlagResolverClient);
    final Integer value = confidence.getValue("wrong-schema-flag", 20);
    assertEquals(20, value);

    final FlagEvaluation<Integer> evaluation = confidence.getEvaluation("wrong-schema-flag", 20);

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
  void internalError() {
    final Confidence confidence = Confidence.create(fakeEngine, new FailingFlagResolverClient());
    final Integer value = confidence.getValue("no-match-flag", 20);
    assertEquals(20, value);

    final FlagEvaluation<Integer> evaluation = confidence.getEvaluation("no-match-flag", 20);

    assertEquals(20, evaluation.getValue());
    assertEquals("", evaluation.getVariant());
    assertEquals("ERROR", evaluation.getReason());
    assertEquals(ErrorType.INTERNAL_ERROR, evaluation.getErrorType().get());
    assertTrue(
        evaluation.getErrorMessage().get().startsWith("Crashing while performing network call"));
  }

  public static class FailingFlagResolverClient implements FlagResolverClient {

    @Override
    public CompletableFuture<ResolveFlagsResponse> resolveFlags(String flag, Struct context) {
      throw new RuntimeException("Crashing while performing network call");
    }

    @Override
    public void close() throws IOException {
      // NOOP
    }
  }
}
