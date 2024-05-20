package com.spotify.confidence;

import static org.junit.jupiter.api.Assertions.*;

import com.google.common.collect.Maps;
import dev.openfeature.sdk.EvaluationContext;
import dev.openfeature.sdk.ImmutableContext;
import dev.openfeature.sdk.Structure;
import dev.openfeature.sdk.Value;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class OpenFeatureUtilsTest {

  @Test
  public void testConvertingEmptyEvaluationContext() {
    final EvaluationContext e = new ImmutableContext();
    final ConfidenceValue.Struct confidenceValue = OpenFeatureUtils.convert(e);
    assertEquals(0, confidenceValue.asMap().size());
  }

  @Test
  public void testConvertingFullEvaluationContext() {
    final Map<String, Value> attributes = Maps.newHashMap();
    attributes.put("key1", new Value("value1"));
    attributes.put("key3", new Value(1.0));
    attributes.put("key4", new Value(true));
    // currently not supported
    //        attributes.put("key5", new Value(Instant.parse("2021-01-01T00:00:00Z")));
    // fill with list
    attributes.put("key6", new Value(List.of(new Value(1), new Value(2), new Value(3))));
    attributes.put(
        "key7",
        new Value(
            Structure.mapToStructure(
                Map.of("subKey1", new Value("subValue1"), "subKey2", new Value(13.37)))));

    final EvaluationContext e = new ImmutableContext("targetingKey", attributes);

    final ConfidenceValue.Struct confidenceValue = OpenFeatureUtils.convert(e);
    assertEquals(6, confidenceValue.asMap().size());
    assertEquals("value1", confidenceValue.asMap().get("key1").asString());
    assertEquals(1.0, confidenceValue.asMap().get("key3").asDouble());
    assertEquals(true, confidenceValue.asMap().get("key4").asBoolean());
    //        assertEquals(Instant.parse("2021-01-01T00:00:00Z"),
    // confidenceValue.asMap().get("key5").asInstant());
    assertEquals(
        List.of(1.0, 2.0, 3.0),
        confidenceValue.asMap().get("key6").asList().stream()
            .map(ConfidenceValue::asDouble)
            .collect(Collectors.toList()));
    assertEquals(
        "subValue1",
        confidenceValue.asMap().get("key7").asStruct().asMap().get("subKey1").asString());
    assertEquals(
        13.37, confidenceValue.asMap().get("key7").asStruct().asMap().get("subKey2").asDouble());
  }
}
