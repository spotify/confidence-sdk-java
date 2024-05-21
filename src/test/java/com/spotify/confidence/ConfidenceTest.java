package com.spotify.confidence;

import static org.junit.jupiter.api.Assertions.*;

import com.spotify.confidence.ConfidenceValue.Struct;
import java.util.List;
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
}
