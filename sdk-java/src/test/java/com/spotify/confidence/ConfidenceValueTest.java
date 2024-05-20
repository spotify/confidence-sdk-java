package com.spotify.confidence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import com.google.common.collect.ImmutableList;
import com.google.protobuf.ListValue;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
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
    assertThat(value.isInteger()).isFalse();
    assertThat(value.isDouble()).isFalse();
    assertThat(value.isTimestamp()).isFalse();
    assertThat(value.isDate()).isFalse();
    assertThat(value.isList()).isFalse();
    assertThrows(IllegalStateException.class, value::asStruct);
    assertThrows(IllegalStateException.class, value::asString);
    assertThrows(IllegalStateException.class, value::asInteger);
    assertThrows(IllegalStateException.class, value::asDouble);
    assertThrows(IllegalStateException.class, value::asInstant);
    assertThrows(IllegalStateException.class, value::asLocalDate);
    assertThrows(IllegalStateException.class, value::asList);
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
  public void testIntegerValue() {
    final ConfidenceValue integerValue = ConfidenceValue.of(42);
    assertTrue(integerValue.isInteger());
    assertEquals(42, integerValue.asInteger());
  }

  @Test
  public void testDoubleValue() {
    final ConfidenceValue doubleValue = ConfidenceValue.of(42.0);
    assertTrue(doubleValue.isDouble());
    assertEquals(42.0, doubleValue.asDouble());
  }

  @Test
  public void testDoubleFromProto() {
    final com.google.protobuf.Value integerProtoValue =
        com.google.protobuf.Value.newBuilder().setNumberValue(42).build();
    final com.google.protobuf.Value doubleProtoValue =
        com.google.protobuf.Value.newBuilder().setNumberValue(42.0).build();

    final ConfidenceValue value1 = ConfidenceValue.fromProto(integerProtoValue);
    final ConfidenceValue value2 = ConfidenceValue.fromProto(doubleProtoValue);
    assertTrue(value1.isDouble());
    assertTrue(value2.isDouble());
    assertEquals(42.0, value1.asDouble());
    assertEquals(42.0, value2.asDouble());
  }

  @Test
  public void testTimestampValue() {
    final Instant instant = Instant.parse("2007-12-03T10:15:30.00Z");
    final ConfidenceValue timestampValue = ConfidenceValue.of(instant);
    assertTrue(timestampValue.isTimestamp());
    assertEquals(instant, timestampValue.asInstant());
  }

  @Test
  public void testTimestampFromProto() {
    final String isoString = "2007-12-03T10:15:30.00Z";
    final com.google.protobuf.Value timestampProtoValue =
        com.google.protobuf.Value.newBuilder().setStringValue(isoString).build();

    final ConfidenceValue timestampValue = ConfidenceValue.fromProto(timestampProtoValue);
    assertTrue(timestampValue.isTimestamp());
    assertEquals(Instant.parse(isoString), timestampValue.asInstant());
  }

  @Test
  public void testDateValue() {
    final LocalDate localDate = LocalDate.parse("2007-12-03");
    final ConfidenceValue timestampValue = ConfidenceValue.of(localDate);
    assertTrue(timestampValue.isDate());
    assertEquals(localDate, timestampValue.asLocalDate());
  }

  @Test
  public void testDateFromProto() {
    final String isoString = "2007-12-03";
    final com.google.protobuf.Value dateProtoValue =
        com.google.protobuf.Value.newBuilder().setStringValue(isoString).build();

    final ConfidenceValue dateValue = ConfidenceValue.fromProto(dateProtoValue);
    assertTrue(dateValue.isDate());
    assertEquals(LocalDate.parse(isoString), dateValue.asLocalDate());
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
  public void testStringListValue() {
    final ConfidenceValue.List listValue =
        ConfidenceValue.ofStrings(ImmutableList.of("item1", "item2"));
    assertTrue(listValue.isList());
    assertEquals(
        ImmutableList.of(ConfidenceValue.of("item1"), ConfidenceValue.of("item2")),
        listValue.asList());
    assertEquals(
        listValue.toProto(),
        com.google.protobuf.Value.newBuilder()
            .setListValue(
                ListValue.newBuilder()
                    .addAllValues(
                        Stream.of(ConfidenceValue.of("item1"), ConfidenceValue.of("item2"))
                            .map(ConfidenceValue::toProto)
                            .collect(Collectors.toList())))
            .build());
  }

  @Test
  public void testIntegerListValue() {
    final ConfidenceValue.List listValue = ConfidenceValue.ofIntegers(ImmutableList.of(3, 5));
    assertEquals(
        listValue.toProto(),
        com.google.protobuf.Value.newBuilder()
            .setListValue(
                ListValue.newBuilder()
                    .addAllValues(
                        Stream.of(ConfidenceValue.of(3), ConfidenceValue.of(5))
                            .map(ConfidenceValue::toProto)
                            .collect(Collectors.toList())))
            .build());
  }

  @Test
  public void testDoubleListValue() {
    final ConfidenceValue.List listValue = ConfidenceValue.ofDoubles(ImmutableList.of(3.1, 4.2));
    assertEquals(
        listValue.toProto(),
        com.google.protobuf.Value.newBuilder()
            .setListValue(
                ListValue.newBuilder()
                    .addAllValues(
                        Stream.of(ConfidenceValue.of(3.1), ConfidenceValue.of(4.2))
                            .map(ConfidenceValue::toProto)
                            .collect(Collectors.toList())))
            .build());
  }

  @Test
  public void testTimestampListValue() {
    final Instant instant1 = Instant.parse("2007-12-03T10:15:30.00Z");
    final Instant instant2 = Instant.parse("2007-12-03T10:15:31.00Z");

    final ConfidenceValue.List listValue =
        ConfidenceValue.ofTimestamps(ImmutableList.of(instant1, instant2));
    assertEquals(
        listValue.toProto(),
        com.google.protobuf.Value.newBuilder()
            .setListValue(
                ListValue.newBuilder()
                    .addAllValues(
                        Stream.of(ConfidenceValue.of(instant1), ConfidenceValue.of(instant2))
                            .map(ConfidenceValue::toProto)
                            .collect(Collectors.toList())))
            .build());
  }

  @Test
  public void testDateListValue() {
    final LocalDate date1 = LocalDate.parse("2007-12-03");
    final LocalDate date2 = LocalDate.parse("2007-12-04");

    final ConfidenceValue.List listValue = ConfidenceValue.ofDates(ImmutableList.of(date1, date2));
    assertEquals(
        listValue.toProto(),
        com.google.protobuf.Value.newBuilder()
            .setListValue(
                ListValue.newBuilder()
                    .addAllValues(
                        Stream.of(ConfidenceValue.of(date1), ConfidenceValue.of(date2))
                            .map(ConfidenceValue::toProto)
                            .collect(Collectors.toList())))
            .build());
  }

  @Test
  public void testBooleanListValue() {
    final ConfidenceValue.List listValue =
        ConfidenceValue.ofBooleans(ImmutableList.of(true, false));
    assertEquals(
        listValue.toProto(),
        com.google.protobuf.Value.newBuilder()
            .setListValue(
                ListValue.newBuilder()
                    .addAllValues(
                        Stream.of(ConfidenceValue.of(true), ConfidenceValue.of(false))
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
    assertThrows(IllegalStateException.class, value::asInteger);
    assertThrows(IllegalStateException.class, value::asDouble);
    assertThrows(IllegalStateException.class, value::asInstant);
    assertThrows(IllegalStateException.class, value::asLocalDate);
    assertThrows(IllegalStateException.class, value::asBoolean);
    assertThrows(IllegalStateException.class, value::asStruct);
  }

  @Test
  public void testStructEmpty() {
    final ConfidenceValue value = ConfidenceValue.Struct.EMPTY;
    assertTrue(value.isStruct());
    assertThrows(IllegalStateException.class, value::asInteger);
    assertThrows(IllegalStateException.class, value::asDouble);
    assertThrows(IllegalStateException.class, value::asLocalDate);
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
    final Instant instant = Instant.parse("2007-12-03T10:15:30.00Z");
    final LocalDate localDate = LocalDate.parse("2007-12-03");
    final Map<String, ConfidenceValue> map = new HashMap<>();
    map.put("string", ConfidenceValue.of("value"));
    map.put("integer", ConfidenceValue.of(42));
    map.put("double", ConfidenceValue.of(42.0));
    map.put("timestamp", ConfidenceValue.of(instant));
    map.put("date", ConfidenceValue.of(localDate));
    map.put("boolean", ConfidenceValue.of(false));
    map.put("list", ConfidenceValue.ofStrings(List.of("item1", "item2")));
    map.put("timestamp_list", ConfidenceValue.ofTimestamps(List.of(instant, instant)));
    map.put("struct", ConfidenceValue.of(map));
    final ConfidenceValue.Struct struct = ConfidenceValue.of(map);
    assertEquals(
        "{date=2007-12-03, struct={date=2007-12-03, boolean=false, string=value, "
            + "double=42.0, timestamp_list=[[2007-12-03T10:15:30Z, 2007-12-03T10:15:30Z]], "
            + "integer=42, list=[[item1, item2]], timestamp=2007-12-03T10:15:30Z}, boolean=false, "
            + "string=value, double=42.0, timestamp_list=[[2007-12-03T10:15:30Z, 2007-12-03T10:15:30Z]], "
            + "integer=42, list=[[item1, item2]], timestamp=2007-12-03T10:15:30Z}",
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
    final Instant instant = Instant.parse("2007-12-03T10:15:30.00Z");
    final LocalDate date = LocalDate.parse("2007-12-03");
    final List<String> stringList = List.of("string1", "string2");
    final ConfidenceValue.Struct struct =
        ConfidenceValue.Struct.builder()
            .set("key1", "value")
            .set("key2", 42)
            .set("key3", true)
            .set("key4", 42.0)
            .set("key5", instant)
            .set("key6", date)
            .setStrings("key7", ImmutableList.of("string1", "string2"))
            .build();
    assertEquals(ConfidenceValue.of("value"), struct.get("key1"));
    assertEquals(ConfidenceValue.of(42), struct.get("key2"));
    assertEquals(ConfidenceValue.of(true), struct.get("key3"));
    assertEquals(ConfidenceValue.of(42.0), struct.get("key4"));
    assertEquals(ConfidenceValue.of(instant), struct.get("key5"));
    assertEquals(ConfidenceValue.of(date), struct.get("key6"));
    assertEquals(ConfidenceValue.ofStrings(stringList), struct.get("key7"));
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
