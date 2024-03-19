package com.spotify.confidence;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.protobuf.ListValue;
import com.google.protobuf.NullValue;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.stream.Collectors;

public abstract class ConfidenceValue<T> {

  private T value;

  static final ConfidenceValue NULL_VALUE =
      new ConfidenceValue<>() {

        @Override
        public boolean isNull() {
          return true;
        }

        @Override
        public com.google.protobuf.Value toProto() {
          return com.google.protobuf.Value.newBuilder().setNullValue(NullValue.NULL_VALUE).build();
        }
      };

  private ConfidenceValue() {}

  public boolean isStruct() {
    return false;
  }

  public boolean isBoolean() {
    return false;
  }

  public boolean isString() {
    return false;
  }

  public boolean isInteger() {
    return false;
  }

  public boolean isDouble() {
    return false;
  }

  public boolean isTimestamp() {
    return false;
  }

  public boolean isDate() {
    return false;
  }

  public boolean isNull() {
    return false;
  }

  public boolean isList() {
    return false;
  }

  public Struct asStruct() {
    throw new IllegalStateException("Not a StructValue");
  }

  public String asString() {
    throw new IllegalStateException("Not a StringValue");
  }

  public int asInteger() {
    throw new IllegalStateException("Not a IntegerValue");
  }

  public double asDouble() {
    throw new IllegalStateException("Not a DoubleValue");
  }

  public Instant asInstant() {
    throw new IllegalStateException("Not a InstantValue");
  }

  public LocalDate asLocalDate() {
    throw new IllegalStateException("Not a DateValue");
  }

  public boolean asBoolean() {
    throw new IllegalStateException("Not a BooleanValue");
  }

  public java.util.List<ConfidenceValue> asList() {
    throw new IllegalStateException("Not a ListValue");
  }

  public static ConfidenceValue<java.lang.Integer> of(int value) {
    return new Integer(value);
  }

  public static ConfidenceValue<java.lang.Double> of(double value) {
    return new Double(value);
  }

  public static ConfidenceValue<Instant> of(Instant value) {
    return new Timestamp(value);
  }

  public static ConfidenceValue<LocalDate> of(LocalDate date) {
    return new Date(date);
  }

  public static ConfidenceValue<String> of(String value) {
    return new StringValue(value);
  }

  public static ConfidenceValue<Boolean> of(boolean value) {
    return new BooleanValue(value);
  }

  public static ConfidenceValue.List<String> ofStrings(
      java.util.List<ConfidenceValue<String>> values) {
    java.util.List<ConfidenceValue<String>> collect =
        values.stream().map(v -> ConfidenceValue.of(v.asString())).collect(Collectors.toList());
    return new List<>(collect);
  }

  public static ConfidenceValue.List<Boolean> ofBooleans(
      java.util.List<ConfidenceValue<Boolean>> values) {
    java.util.List<ConfidenceValue<Boolean>> collect =
        values.stream().map(v -> ConfidenceValue.of(v.asBoolean())).collect(Collectors.toList());
    return new List<>(collect);
  }

  public static ConfidenceValue.List<java.lang.Integer> ofIntegers(
      java.util.List<ConfidenceValue<java.lang.Integer>> values) {
    java.util.List<ConfidenceValue<java.lang.Integer>> collect =
        values.stream().map(v -> ConfidenceValue.of(v.asInteger())).collect(Collectors.toList());
    return new List<>(collect);
  }

  public static ConfidenceValue.List<java.lang.Double> ofDoubles(
      java.util.List<ConfidenceValue<java.lang.Double>> values) {
    java.util.List<ConfidenceValue<java.lang.Double>> collect =
        values.stream().map(v -> ConfidenceValue.of(v.asDouble())).collect(Collectors.toList());
    return new List<>(collect);
  }

  public static ConfidenceValue.List<Instant> ofTimestamps(
      java.util.List<ConfidenceValue<Instant>> values) {
    java.util.List<ConfidenceValue<Instant>> collect =
        values.stream().map(v -> ConfidenceValue.of(v.asInstant())).collect(Collectors.toList());
    return new List<>(collect);
  }

  public static ConfidenceValue.List<LocalDate> ofDates(
      java.util.List<ConfidenceValue<LocalDate>> values) {
    java.util.List<ConfidenceValue<LocalDate>> collect =
        values.stream().map(v -> ConfidenceValue.of(v.asLocalDate())).collect(Collectors.toList());
    return new List<>(collect);
  }

  public static Struct of(Map<String, ConfidenceValue> values) {
    return new Struct(values);
  }

  static ConfidenceValue fromProto(com.google.protobuf.Value protoValue) {
    final com.google.protobuf.Value.KindCase kind = protoValue.getKindCase();
    switch (kind) {
      case BOOL_VALUE:
        return ConfidenceValue.of(protoValue.getBoolValue());
      case NUMBER_VALUE:
        return ConfidenceValue.of(protoValue.getNumberValue());
      case STRING_VALUE:
        final String stringValue = protoValue.getStringValue();
        try {
          return ConfidenceValue.of(Instant.parse(stringValue));
        } catch (Exception e1) {
          try {
            return ConfidenceValue.of(LocalDate.parse(stringValue));
          } catch (Exception e2) {
            return ConfidenceValue.of(stringValue);
          }
        }
      case NULL_VALUE:
        return NULL_VALUE;
      case STRUCT_VALUE:
        return Struct.fromProto(protoValue.getStructValue());
      case LIST_VALUE:
        final java.util.List<ConfidenceValue> list =
            protoValue.getListValue().getValuesList().stream()
                .map(ConfidenceValue::fromProto)
                .collect(Collectors.toList());
        return new List(list);
    }
    throw new IllegalArgumentException("Unsupported value kind:" + kind);
  }

  public abstract com.google.protobuf.Value toProto();

  public static class StringValue extends ConfidenceValue<String> {

    private StringValue(String value) {
      super.value = value;
    }

    @Override
    public boolean isString() {
      return true;
    }

    @Override
    public String asString() {
      return super.value;
    }

    @Override
    public com.google.protobuf.Value toProto() {
      return com.google.protobuf.Value.newBuilder().setStringValue(super.value).build();
    }

    @Override
    public String toString() {
      return super.value;
    }
  }

  public static class BooleanValue extends ConfidenceValue<Boolean> {

    private BooleanValue(boolean value) {
      super.value = value;
    }

    @Override
    public boolean isBoolean() {
      return true;
    }

    @Override
    public boolean asBoolean() {
      return super.value;
    }

    @Override
    public String toString() {
      return String.valueOf(super.value);
    }

    @Override
    public com.google.protobuf.Value toProto() {
      return com.google.protobuf.Value.newBuilder().setBoolValue(super.value).build();
    }
  }

  public static class Integer extends ConfidenceValue<java.lang.Integer> {

    private final int value;

    private Integer(int value) {
      this.value = value;
    }

    @Override
    public boolean isInteger() {
      return true;
    }

    @Override
    public int asInteger() {
      return value;
    }

    @Override
    public String toString() {
      return String.valueOf(value);
    }

    @Override
    public com.google.protobuf.Value toProto() {
      return com.google.protobuf.Value.newBuilder().setNumberValue(value).build();
    }
  }

  public static class Double extends ConfidenceValue<java.lang.Double> {

    private Double(double value) {
      super.value = value;
    }

    @Override
    public boolean isDouble() {
      return true;
    }

    @Override
    public double asDouble() {
      return super.value;
    }

    @Override
    public String toString() {
      return String.valueOf(super.value);
    }

    @Override
    public com.google.protobuf.Value toProto() {
      return com.google.protobuf.Value.newBuilder().setNumberValue(super.value).build();
    }
  }

  public static class Timestamp extends ConfidenceValue<Instant> {

    private Timestamp(Instant value) {
      super.value = value;
    }

    @Override
    public boolean isTimestamp() {
      return true;
    }

    @Override
    public Instant asInstant() {
      return super.value;
    }

    @Override
    public String toString() {
      return String.valueOf(super.value);
    }

    @Override
    public com.google.protobuf.Value toProto() {
      return com.google.protobuf.Value.newBuilder().setStringValue(super.value.toString()).build();
    }
  }

  public static class Date extends ConfidenceValue<LocalDate> {

    private Date(LocalDate value) {
      super.value = value;
    }

    @Override
    public boolean isDate() {
      return true;
    }

    @Override
    public LocalDate asLocalDate() {
      return super.value;
    }

    @Override
    public String toString() {
      return String.valueOf(super.value);
    }

    @Override
    public com.google.protobuf.Value toProto() {
      return com.google.protobuf.Value.newBuilder().setStringValue(super.value.toString()).build();
    }
  }

  public static class List<T> extends ConfidenceValue {
    private final ImmutableList<ConfidenceValue<T>> values;

    private List(java.util.List<ConfidenceValue<T>> values) {
      this.values = ImmutableList.copyOf(values);
    }

    @Override
    public boolean isList() {
      return true;
    }

    @Override
    public java.util.List<ConfidenceValue> asList() {
      ImmutableList<ConfidenceValue> confidenceValues = ImmutableList.copyOf(values);
      return confidenceValues;
    }

    @Override
    public String toString() {
      return "[" + values + "]";
    }

    @Override
    public com.google.protobuf.Value toProto() {
      final ListValue value =
          ListValue.newBuilder()
              .addAllValues(
                  values.stream().map(ConfidenceValue::toProto).collect(Collectors.toList()))
              .build();
      return com.google.protobuf.Value.newBuilder().setListValue(value).build();
    }

    static List fromProto(ListValue list) {
      return new List(
          list.getValuesList().stream()
              .map(ConfidenceValue::fromProto)
              .collect(Collectors.toList()));
    }
  }

  public static class Struct extends ConfidenceValue {
    public static final Struct EMPTY = new Struct(ImmutableMap.of());
    private final ImmutableMap<String, ConfidenceValue> values;

    protected Struct(Map<String, ConfidenceValue> values) {
      this.values = ImmutableMap.copyOf(values);
    }

    @Override
    public boolean isStruct() {
      return true;
    }

    @Override
    public Struct asStruct() {
      return new Struct(values);
    }

    public ConfidenceValue get(String... path) {
      ConfidenceValue value = this;
      for (int i = 0; i < path.length; i++) {
        if (!value.isStruct()) {
          // todo better error
          throw new IllegalStateException();
        }
        value = values.getOrDefault(path[i], NULL_VALUE);
      }
      return value;
    }

    @Override
    public String toString() {
      return values.toString();
    }

    public static Builder builder() {
      return new Builder();
    }

    @Override
    public com.google.protobuf.Value toProto() {
      final com.google.protobuf.Struct.Builder builder = com.google.protobuf.Struct.newBuilder();
      values.forEach((key, value) -> builder.putFields(key, value.toProto()));
      return com.google.protobuf.Value.newBuilder().setStructValue(builder).build();
    }

    static Struct fromProto(com.google.protobuf.Struct struct) {
      return new Struct(Maps.transformValues(struct.getFieldsMap(), ConfidenceValue::fromProto));
    }

    public Map<String, ConfidenceValue> asMap() {
      return values;
    }

    public Map<String, com.google.protobuf.Value> asProtoMap() {
      return values.entrySet().stream()
          .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().toProto()));
    }

    public static final class Builder {

      private final ImmutableMap.Builder<String, ConfidenceValue> builder = ImmutableMap.builder();

      public Builder set(String key, ConfidenceValue value) {
        if (!value.isNull()) builder.put(key, value);
        return this;
      }

      public Builder set(String key, int value) {
        return set(key, ConfidenceValue.of(value));
      }

      public Builder set(String key, double value) {
        return set(key, ConfidenceValue.of(value));
      }

      public Builder set(String key, Instant value) {
        return set(key, ConfidenceValue.of(value));
      }

      public Builder set(String key, LocalDate value) {
        return set(key, ConfidenceValue.of(value));
      }

      public Builder set(String key, String value) {
        return set(key, ConfidenceValue.of(value));
      }

      public Builder set(String key, boolean value) {
        return set(key, ConfidenceValue.of(value));
      }

      public Builder set(String key, Builder value) {
        return set(key, value.build());
      }

      Struct build() {
        return new Struct(builder.build());
      }
    }
  }

  @Override
  public int hashCode() {
    return toProto().hashCode();
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == null) {
      return false;
    }
    if (obj.getClass() != this.getClass()) {
      return false;
    }
    return toProto().equals(((ConfidenceValue) obj).toProto());
  }
}
