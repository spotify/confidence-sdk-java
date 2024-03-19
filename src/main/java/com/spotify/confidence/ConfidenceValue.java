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

public abstract class ConfidenceValue {

  static final ConfidenceValue NULL_VALUE =
      new ConfidenceValue() {

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

  public static ConfidenceValue of(int value) {
    return new Integer(value);
  }

  public static ConfidenceValue of(double value) {
    return new Double(value);
  }

  public static ConfidenceValue of(Instant value) {
    return new Timestamp(value);
  }

  public static ConfidenceValue of(LocalDate date) {
    return new Date(date);
  }

  public static ConfidenceValue of(String value) {
    return new StringValue(value);
  }

  public static ConfidenceValue of(boolean value) {
    return new BooleanValue(value);
  }

  public static ConfidenceValue.List ofStrings(java.util.List<String> values) {
    return new StringList(values);
  }

  public static ConfidenceValue.List ofBooleans(java.util.List<Boolean> values) {
    return new BooleanList(values);
  }

  public static ConfidenceValue.List ofIntegers(java.util.List<java.lang.Integer> values) {
    return new IntegerList(values);
  }

  public static ConfidenceValue.List ofDoubles(java.util.List<java.lang.Double> values) {
    return new DoubleList(values);
  }

  public static ConfidenceValue.List ofTimestamps(java.util.List<Instant> values) {
    return new TimestampList(values);
  }

  public static ConfidenceValue.List ofDates(java.util.List<LocalDate> values) {
    return new DateList(values);
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

  public static class StringValue extends ConfidenceValue {
    private final String value;

    private StringValue(String value) {
      this.value = value;
    }

    @Override
    public boolean isString() {
      return true;
    }

    @Override
    public String asString() {
      return value;
    }

    @Override
    public com.google.protobuf.Value toProto() {
      return com.google.protobuf.Value.newBuilder().setStringValue(value).build();
    }

    @Override
    public String toString() {
      return value;
    }
  }

  public static class BooleanValue extends ConfidenceValue {
    private final boolean value;

    private BooleanValue(boolean value) {
      this.value = value;
    }

    @Override
    public boolean isBoolean() {
      return true;
    }

    @Override
    public boolean asBoolean() {
      return value;
    }

    @Override
    public String toString() {
      return String.valueOf(value);
    }

    @Override
    public com.google.protobuf.Value toProto() {
      return com.google.protobuf.Value.newBuilder().setBoolValue(value).build();
    }
  }

  public static class Integer extends ConfidenceValue {

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

  public static class Double extends ConfidenceValue {

    private final double value;

    private Double(double value) {
      this.value = value;
    }

    @Override
    public boolean isDouble() {
      return true;
    }

    @Override
    public double asDouble() {
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

  public static class Timestamp extends ConfidenceValue {

    private final Instant value;

    private Timestamp(Instant value) {
      this.value = value;
    }

    @Override
    public boolean isTimestamp() {
      return true;
    }

    @Override
    public Instant asInstant() {
      return value;
    }

    @Override
    public String toString() {
      return String.valueOf(value);
    }

    @Override
    public com.google.protobuf.Value toProto() {
      return com.google.protobuf.Value.newBuilder().setStringValue(value.toString()).build();
    }
  }

  public static class Date extends ConfidenceValue {

    private final LocalDate value;

    private Date(LocalDate value) {
      this.value = value;
    }

    @Override
    public boolean isDate() {
      return true;
    }

    @Override
    public LocalDate asLocalDate() {
      return value;
    }

    @Override
    public String toString() {
      return String.valueOf(value);
    }

    @Override
    public com.google.protobuf.Value toProto() {
      return com.google.protobuf.Value.newBuilder().setStringValue(value.toString()).build();
    }
  }

  public static class StringList extends List {
    public StringList(java.util.List<String> values) {
      super(values.stream().map(ConfidenceValue::of).collect(Collectors.toList()));
    }
  }

  public static class BooleanList extends List {
    public BooleanList(java.util.List<Boolean> values) {
      super(values.stream().map(ConfidenceValue::of).collect(Collectors.toList()));
    }
  }

  public static class IntegerList extends List {
    public IntegerList(java.util.List<java.lang.Integer> values) {
      super(values.stream().map(ConfidenceValue::of).collect(Collectors.toList()));
    }
  }

  public static class DoubleList extends List {
    public DoubleList(java.util.List<java.lang.Double> values) {
      super(values.stream().map(ConfidenceValue::of).collect(Collectors.toList()));
    }
  }

  public static class TimestampList extends List {
    public TimestampList(java.util.List<Instant> values) {
      super(values.stream().map(ConfidenceValue::of).collect(Collectors.toList()));
    }
  }

  public static class DateList extends List {
    public DateList(java.util.List<LocalDate> values) {
      super(values.stream().map(ConfidenceValue::of).collect(Collectors.toList()));
    }
  }

  public static class List extends ConfidenceValue {
    private final ImmutableList<ConfidenceValue> values;

    private List(java.util.List<ConfidenceValue> values) {
      this.values = ImmutableList.copyOf(values);
    }

    @Override
    public boolean isList() {
      return true;
    }

    @Override
    public java.util.List<ConfidenceValue> asList() {
      return ImmutableList.copyOf(values);
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
