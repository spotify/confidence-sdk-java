package com.spotify.confidence.eventsender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import com.google.protobuf.ListValue;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class ValueTest {

  @Test
  public void testNull() {
    Value value = Value.NULL_VALUE;
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
    Value stringValue = Value.of("test value");
    assertTrue(stringValue.isString());
    assertEquals("test value", stringValue.asString());
  }

  @Test
  public void testBooleanValue() {
    Value booleanValue = Value.of(true);
    assertTrue(booleanValue.isBoolean());
    assertTrue(booleanValue.asBoolean());
  }

  @Test
  public void testBooleanFromProto() {
    com.google.protobuf.Value protoValue =
        com.google.protobuf.Value.newBuilder().setBoolValue(true).build();
    Value booleanValue = Value.fromProto(protoValue);
    assertTrue(booleanValue.isBoolean());
    assertTrue(booleanValue.asBoolean());
  }

  @Test
  public void testNumberValue() {
    Value numberValue = Value.of(42.0);
    assertTrue(numberValue.isNumber());
    assertEquals(42.0, numberValue.asNumber());
  }

  @Test
  public void testNumberFromProto() {
    com.google.protobuf.Value protoValue =
        com.google.protobuf.Value.newBuilder().setNumberValue(42.0).build();
    Value numberValue = Value.fromProto(protoValue);
    assertTrue(numberValue.isNumber());
    assertEquals(42.0, numberValue.asNumber());
  }

  @Test
  public void testNullValue() {
    assertTrue(Value.NULL_VALUE.isNull());
  }

  @Test
  public void testStructValue() {
    Map<String, Value> map = new HashMap<>();
    map.put("key", Value.of("value"));
    Value structValue = Value.of(map);
    assertTrue(structValue.isStruct());
    assertEquals("value", structValue.asStruct().get("key").asString());
  }

  @Test
  public void testListValue() {
    Value.List listValue = new Value.List(Arrays.asList(Value.of("item1"), Value.of("item2")));
    assertEquals(
        listValue.toProto(),
        com.google.protobuf.Value.newBuilder()
            .setListValue(
                ListValue.newBuilder()
                    .addAllValues(
                        Arrays.asList(Value.of("item1"), Value.of("item2")).stream()
                            .map(Value::toProto)
                            .collect(Collectors.toList())))
            .build());
  }

  @Test
  public void testStringFromProto() {
    Value fromProtoValue = Value.fromProto(Value.of("test value").toProto());
    assertTrue(fromProtoValue.isString());
    assertEquals("test value", fromProtoValue.asString());
  }

  @Test
  public void testToProto() {
    Value value = Value.of("test value");
    com.google.protobuf.Value protoValue = value.toProto();
    assertEquals(com.google.protobuf.Value.KindCase.STRING_VALUE, protoValue.getKindCase());
    assertEquals("test value", protoValue.getStringValue());
  }

  @Test
  public void testStructAsProtoMap() {
    Map<String, Value> map = new HashMap<>();
    map.put("key", Value.of("value"));
    Value.Struct structValue = Value.of(map);
    Map<String, com.google.protobuf.Value> protoMap = structValue.asProtoMap();
    assertTrue(protoMap.containsKey("key"));
    assertEquals("value", protoMap.get("key").getStringValue());
  }

  @Test
  public void testStructBuilder() {
    Value.Struct.Builder builder = Value.Struct.builder();
    builder.set("key", "value");
    Value.Struct structValue = builder.build();
    assertTrue(structValue.isStruct());
    assertEquals("value", structValue.get("key").asString());
  }

  @Test
  public void testStructToProto() {
    Value.Struct.Builder builder = Value.Struct.builder();
    builder.set("key", "value");
    Value.Struct structValue = builder.build();
    com.google.protobuf.Value proto = structValue.toProto();
    assertEquals(com.google.protobuf.Value.KindCase.STRUCT_VALUE, proto.getKindCase());
    assertEquals("value", proto.getStructValue().getFieldsOrThrow("key").getStringValue());
  }

  @Test
  public void testListFromProto() {
    ListValue protoListValue =
        ListValue.newBuilder()
            .addAllValues(
                Stream.of(Value.of("item1"), Value.of("item2"))
                    .map(Value::toProto)
                    .collect(Collectors.toList()))
            .build();
    Value.List listValue = Value.List.fromProto(protoListValue);
    assertEquals(
        listValue.asList().get(0).asString(),
        protoListValue.getValuesList().get(0).getStringValue());
    assertEquals(
        listValue.asList().get(1).asString(),
        protoListValue.getValuesList().get(1).getStringValue());
  }

  @Test
  public void testExceptions() {
    Value value = Value.of("test value");
    assertThrows(IllegalStateException.class, value::asNumber);
    assertThrows(IllegalStateException.class, value::asBoolean);
    assertThrows(IllegalStateException.class, value::asStruct);
  }

  @Test
  public void testStructEmpty() {
    Value value = Value.Struct.EMPTY;
    assertTrue(value.isStruct());
    assertThrows(IllegalStateException.class, value::asNumber);
    assertThrows(IllegalStateException.class, value::asString);
    assertThrows(IllegalStateException.class, value::asBoolean);
  }

  @Test
  public void testStructGet() {
    Map<String, Value> map = new HashMap<>();
    map.put("key", Value.of("value"));
    Value.Struct struct = Value.of(map);
    assertEquals(Value.of("value"), struct.get("key"));
    assertEquals(Value.NULL_VALUE, struct.get("nonexistent"));
  }

  @Test
  public void testStructToString() {
    Map<String, Value> map = new HashMap<>();
    map.put("string", Value.of("value"));
    map.put("number", Value.of(42));
    map.put("boolean", Value.of(false));
    map.put("list", Value.of(java.util.List.of(Value.of("item1"), Value.of("item2"))));
    map.put("struct", Value.of(map));
    Value.Struct struct = Value.of(map);
    assertEquals(
        "{struct={number=42.0, boolean=false, string=value, list=[[item1, item2]]}, number=42.0, boolean=false, string=value, list=[[item1, item2]]}",
        struct.toString());
  }

  @Test
  public void testStructFromProto() {
    com.google.protobuf.Struct protoStruct =
        com.google.protobuf.Struct.newBuilder()
            .putFields("key", Value.of("value").toProto())
            .build();

    Value.Struct struct = Value.Struct.fromProto(protoStruct);
    assertEquals(Value.of("value"), struct.get("key"));
  }

  @Test
  public void testStructAsMap() {
    Map<String, Value> map = new HashMap<>();
    map.put("key", Value.of("value"));
    Value.Struct struct = Value.of(map);
    assertEquals(map, struct.asMap());
  }

  @Test
  public void testStructBuilderSet() {
    Value.Struct.Builder builder = Value.Struct.builder();
    builder.set("key", Value.of("value"));
    Value.Struct struct = builder.build();
    assertEquals(Value.of("value"), struct.get("key"));
  }

  @Test
  public void testStructBuilderSetStringValue() {
    Value.Struct.Builder builder = Value.Struct.builder();
    builder.set("key", "value");
    Value.Struct struct = builder.build();
    assertEquals(Value.of("value"), struct.get("key"));
  }

  @Test
  public void testStructBuilderSetNumberValue() {
    Value.Struct.Builder builder = Value.Struct.builder();
    builder.set("key", 42.0);
    Value.Struct struct = builder.build();
    assertEquals(Value.of(42.0), struct.get("key"));
  }

  @Test
  public void testStructBuilderSetBooleanValue() {
    Value.Struct.Builder builder = Value.Struct.builder();
    builder.set("key", true);
    Value.Struct struct = builder.build();
    assertEquals(Value.of(true), struct.get("key"));
  }

  @Test
  public void testStructGetWithMultipleKeys() {
    Value.Struct.Builder builder = Value.Struct.builder();
    Value.Struct innerStruct = builder.set("innerKey", "innerValue").build();
    builder.set("key", innerStruct);
    Value.Struct structValue = builder.build();
    assertEquals("innerValue", structValue.get("key", "innerKey").asString());
  }

  @Test
  public void testStructGetWithInvalidPath() {
    Value.Struct.Builder builder = Value.Struct.builder();
    builder.set("key", "value");
    Value.Struct structValue = builder.build();
    assertThrows(IllegalStateException.class, () -> structValue.get("key", "invalid"));
  }

  @Test
  public void testStructGetWithMissingKey() {
    Value.Struct.Builder builder = Value.Struct.builder();
    builder.set("key", "value");
    Value.Struct structValue = builder.build();
    assertTrue(structValue.get("missingKey").isNull());
  }

  @Test
  public void testStructIsEmpty() {
    Value.Struct structValue = Value.Struct.EMPTY;
    assertTrue(structValue.asMap().isEmpty());
  }

  @Test
  public void testUnsupportedValueKind() {
    com.google.protobuf.Value protoValue = com.google.protobuf.Value.newBuilder().build();
    assertThrows(IllegalArgumentException.class, () -> Value.fromProto(protoValue));
  }
}
