package com.spotify.confidence;

import static org.junit.jupiter.api.Assertions.*;

import com.google.protobuf.util.Values;
import dev.openfeature.sdk.MutableStructure;
import dev.openfeature.sdk.Value;
import dev.openfeature.sdk.exceptions.ValueNotConvertableError;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TypeMapperTest {

  @Test
  public void testFromIntegerNumber() {
    // given
    final Value intValue = new Value(42);
    // when
    final com.google.protobuf.Value protoValue = TypeMapper.from(intValue);
    // then
    assertEquals(42, protoValue.getNumberValue());
  }

  @Test
  public void testFromDoubleNumber() {
    // given
    final Value intValue = new Value(42.0);
    // when
    final com.google.protobuf.Value protoValue = TypeMapper.from(intValue);
    // then
    assertEquals(42.0, protoValue.getNumberValue());
  }

  @Test
  public void testFromValueWithBoolean() {
    // Create a Value with a boolean
    final Value value = new Value(true);

    // Call the method under test
    final com.google.protobuf.Value protoValue = TypeMapper.from(value);

    // Check the result
    assertTrue(protoValue.getBoolValue());
  }

  @Test
  public void testFromValueWithNull() {
    // Create a Value with a null value
    final Value value = new Value();

    // Call the method under test
    final com.google.protobuf.Value protoValue = TypeMapper.from(value);

    // Check the result
    assertTrue(protoValue.hasNullValue());
  }

  @Test
  public void testFromValueWithInstant() {
    // Create a Value with an Instant
    final Value value = new Value(java.time.Instant.now());

    // Call the method under test
    assertThrows(ValueNotConvertableError.class, () -> TypeMapper.from(value));
  }

  @Test
  public void testFromValueWithString() {
    // Create a Value with a string
    final Value value = new Value("test");

    // Call the method under test
    final com.google.protobuf.Value protoValue = TypeMapper.from(value);

    // Check the result
    assertEquals("test", protoValue.getStringValue());
  }

  @Test
  public void testFromValueWithList() {
    // Create a Value with a list
    final Value value = new Value(Arrays.asList(new Value("item1"), new Value("item2")));

    // Call the method under test
    final com.google.protobuf.Value protoValue = TypeMapper.from(value);

    // Check the result
    assertEquals(
        Arrays.asList(Values.of("item1"), Values.of("item2")),
        protoValue.getListValue().getValuesList());
  }

  @Test
  public void testFromValueWithStructure() {
    // Create a Value with a structure
    final Map<String, Value> map = new HashMap<>();
    map.put("field", new Value("value"));
    final Value value = new Value(new MutableStructure(map));

    // Call the method under test
    final com.google.protobuf.Value protoValue = TypeMapper.from(value);

    // Check the result
    assertEquals(Values.of("value"), protoValue.getStructValue().getFieldsMap().get("field"));
  }
}
