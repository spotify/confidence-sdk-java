package com.spotify.confidence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import com.google.protobuf.ListValue;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class ConfidenceValueTest {

  @Test
  public void testNull() {
    final ConfidenceValue value = ConfidenceValue.NULL_VALUE;
    assertTrue(value.isNull());
    assertThat(value.isStruct()).isFalse();
    assertThat(value.isBoolean()).isFalse();
    assertThat(value.isString()).isFalse();
    assertThat(value.isNumber()).isFalse();
    assertThrows(IllegalStateException.class, value::asStruct);
    assertThrows(IllegalStateException.class, value::asString);
    assertThrows(IllegalStateException.class, value::asNumber);
  }

  @Test
  public void testStringValue() {
    final ConfidenceValue stringValue = ConfidenceValue.of("test value");
    assertTrue(stringValue.isString());
    assertEquals("test value", stringValue.asString());
  }

  @Test
  public void testBooleanValue() {
    final ConfidenceValue booleanValue = ConfidenceValue.of(true);
    assertTrue(booleanValue.isBoolean());
    assertTrue(booleanValue.asBoolean());
  }

  @Test
  public void testBooleanFromProto() {
    final com.google.protobuf.Value protoValue =
        com.google.protobuf.Value.newBuilder().setBoolValue(true).build();
    final ConfidenceValue booleanValue = ConfidenceValue.fromProto(protoValue);
    assertTrue(booleanValue.isBoolean());
    assertTrue(booleanValue.asBoolean());
  }

  @Test
  public void testNumberValue() {
    final ConfidenceValue numberValue = ConfidenceValue.of(42.0);
    assertTrue(numberValue.isNumber());
    assertEquals(42.0, numberValue.asNumber());
  }

  @Test
  public void testNumberFromProto() {
    final com.google.protobuf.Value protoValue =
        com.google.protobuf.Value.newBuilder().setNumberValue(42.0).build();
    final ConfidenceValue numberValue = ConfidenceValue.fromProto(protoValue);
    assertTrue(numberValue.isNumber());
    assertEquals(42.0, numberValue.asNumber());
  }

  @Test
  public void testNullValue() {
    assertTrue(ConfidenceValue.NULL_VALUE.isNull());
  }

  @Test
  public void testStructValue() {
    final Map<String, ConfidenceValue> map = new HashMap<>();
    map.put("key", ConfidenceValue.of("value"));
    final ConfidenceValue structValue = ConfidenceValue.of(map);
    assertTrue(structValue.isStruct());
    assertEquals("value", structValue.asStruct().get("key").asString());
  }

  @Test
  public void testListValue() {
    final ConfidenceValue.List listValue =
        new ConfidenceValue.List(
            Arrays.asList(ConfidenceValue.of("item1"), ConfidenceValue.of("item2")));
    assertEquals(
        listValue.toProto(),
        com.google.protobuf.Value.newBuilder()
            .setListValue(
                ListValue.newBuilder()
                    .addAllValues(
                        Arrays.asList(ConfidenceValue.of("item1"), ConfidenceValue.of("item2"))
                            .stream()
                            .map(ConfidenceValue::toProto)
                            .collect(Collectors.toList())))
            .build());
  }

  @Test
  public void testStringFromProto() {
    final ConfidenceValue fromProtoValue =
        ConfidenceValue.fromProto(ConfidenceValue.of("test value").toProto());
    assertTrue(fromProtoValue.isString());
    assertEquals("test value", fromProtoValue.asString());
  }

  @Test
  public void testToProto() {
    final ConfidenceValue value = ConfidenceValue.of("test value");
    final com.google.protobuf.Value protoValue = value.toProto();
    assertEquals(com.google.protobuf.Value.KindCase.STRING_VALUE, protoValue.getKindCase());
    assertEquals("test value", protoValue.getStringValue());
  }

  @Test
  public void testStructAsProtoMap() {
    final Map<String, ConfidenceValue> map = new HashMap<>();
    map.put("key", ConfidenceValue.of("value"));
    final ConfidenceValue.Struct structValue = ConfidenceValue.of(map);
    final Map<String, com.google.protobuf.Value> protoMap = structValue.asProtoMap();
    assertTrue(protoMap.containsKey("key"));
    assertEquals("value", protoMap.get("key").getStringValue());
  }

  @Test
  public void testStructToProto() {
    final ConfidenceValue.Struct structValue =
        ConfidenceValue.Struct.builder().set("key", "value").build();
    final com.google.protobuf.Value proto = structValue.toProto();
    assertEquals(com.google.protobuf.Value.KindCase.STRUCT_VALUE, proto.getKindCase());
    assertEquals("value", proto.getStructValue().getFieldsOrThrow("key").getStringValue());
  }

  @Test
  public void testListFromProto() {
    final ListValue protoListValue =
        ListValue.newBuilder()
            .addAllValues(
                Stream.of(ConfidenceValue.of("item1"), ConfidenceValue.of("item2"))
                    .map(ConfidenceValue::toProto)
                    .collect(Collectors.toList()))
            .build();
    final ConfidenceValue.List listValue = ConfidenceValue.List.fromProto(protoListValue);
    assertEquals(
        listValue.asList().get(0).asString(),
        protoListValue.getValuesList().get(0).getStringValue());
    assertEquals(
        listValue.asList().get(1).asString(),
        protoListValue.getValuesList().get(1).getStringValue());
  }

  @Test
  public void testExceptions() {
    final ConfidenceValue value = ConfidenceValue.of("test value");
    assertThrows(IllegalStateException.class, value::asNumber);
    assertThrows(IllegalStateException.class, value::asBoolean);
    assertThrows(IllegalStateException.class, value::asStruct);
  }

  @Test
  public void testStructEmpty() {
    final ConfidenceValue value = ConfidenceValue.Struct.EMPTY;
    assertTrue(value.isStruct());
    assertThrows(IllegalStateException.class, value::asNumber);
    assertThrows(IllegalStateException.class, value::asString);
    assertThrows(IllegalStateException.class, value::asBoolean);
  }

  @Test
  public void testStructGet() {
    final Map<String, ConfidenceValue> map = new HashMap<>();
    map.put("key", ConfidenceValue.of("value"));
    final ConfidenceValue.Struct struct = ConfidenceValue.of(map);
    assertEquals(ConfidenceValue.of("value"), struct.get("key"));
    assertEquals(ConfidenceValue.NULL_VALUE, struct.get("nonexistent"));
  }

  @Test
  public void testStructToString() {
    final Map<String, ConfidenceValue> map = new HashMap<>();
    map.put("string", ConfidenceValue.of("value"));
    map.put("number", ConfidenceValue.of(42));
    map.put("boolean", ConfidenceValue.of(false));
    map.put(
        "list",
        ConfidenceValue.of(
            java.util.List.of(ConfidenceValue.of("item1"), ConfidenceValue.of("item2"))));
    map.put("struct", ConfidenceValue.of(map));
    final ConfidenceValue.Struct struct = ConfidenceValue.of(map);
    assertEquals(
        "{struct={number=42.0, boolean=false, string=value,"
            + " list=[[item1, item2]]}, number=42.0, boolean=false, "
            + "string=value, list=[[item1, item2]]}",
        struct.toString());
  }

  @Test
  public void testStructFromProto() {
    final com.google.protobuf.Struct protoStruct =
        com.google.protobuf.Struct.newBuilder()
            .putFields("key", ConfidenceValue.of("value").toProto())
            .build();

    final ConfidenceValue.Struct struct = ConfidenceValue.Struct.fromProto(protoStruct);
    assertEquals(ConfidenceValue.of("value"), struct.get("key"));
  }

  @Test
  public void testStructAsMap() {
    final Map<String, ConfidenceValue> map = new HashMap<>();
    map.put("key", ConfidenceValue.of("value"));
    final ConfidenceValue.Struct struct = ConfidenceValue.of(map);
    assertEquals(map, struct.asMap());
  }

  @Test
  public void testStructBuilderSetValues() {
    final ConfidenceValue.Struct struct =
        ConfidenceValue.Struct.builder()
            .set("key1", "value")
            .set("key2", 42.0)
            .set("key3", true)
            .build();
    assertEquals(ConfidenceValue.of("value"), struct.get("key1"));
    assertEquals(ConfidenceValue.of(42.0), struct.get("key2"));
    assertEquals(ConfidenceValue.of(true), struct.get("key3"));
  }

  @Test
  public void testStructGetWithMultipleKeys() {
    final ConfidenceValue.Struct.Builder builder = ConfidenceValue.Struct.builder();
    final ConfidenceValue.Struct innerStruct = builder.set("innerKey", "innerValue").build();
    builder.set("key", innerStruct);
    final ConfidenceValue.Struct structValue = builder.build();
    assertEquals("innerValue", structValue.get("key", "innerKey").asString());
  }

  @Test
  public void testStructGetWithInvalidPath() {
    final ConfidenceValue.Struct.Builder builder = ConfidenceValue.Struct.builder();
    builder.set("key", "value");
    final ConfidenceValue.Struct structValue = builder.build();
    assertThrows(IllegalStateException.class, () -> structValue.get("key", "invalid"));
  }

  @Test
  public void testStructGetWithMissingKey() {
    final ConfidenceValue.Struct.Builder builder = ConfidenceValue.Struct.builder();
    builder.set("key", "value");
    final ConfidenceValue.Struct structValue = builder.build();
    assertTrue(structValue.get("missingKey").isNull());
  }

  @Test
  public void testStructIsEmpty() {
    final ConfidenceValue.Struct structValue = ConfidenceValue.Struct.EMPTY;
    assertTrue(structValue.asMap().isEmpty());
  }

  @Test
  public void testUnsupportedValueKind() {
    final com.google.protobuf.Value protoValue = com.google.protobuf.Value.newBuilder().build();
    assertThrows(IllegalArgumentException.class, () -> ConfidenceValue.fromProto(protoValue));
  }
}
