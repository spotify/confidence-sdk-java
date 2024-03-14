package com.spotify.confidence.eventsender;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.protobuf.ListValue;
import com.google.protobuf.NullValue;
import java.util.Map;
import java.util.stream.Collectors;

abstract class Value {

  static final Value NULL_VALUE =
      new Value() {

        @Override
        public boolean isNull() {
          return true;
        }

        @Override
        com.google.protobuf.Value toProto() {
          return com.google.protobuf.Value.newBuilder().setNullValue(NullValue.NULL_VALUE).build();
        }
      };

  private Value() {}

  public boolean isStruct() {
    return false;
  }

  public boolean isBoolean() {
    return false;
  }

  public boolean isString() {
    return false;
  }

  public boolean isNumber() {
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

  public double asNumber() {
    throw new IllegalStateException("Not a NumberValue");
  }

  public boolean asBoolean() {
    throw new IllegalStateException("Not a BooleanValue");
  }

  public java.util.List<Value> asList() {
    throw new IllegalStateException("Not a ListValue");
  }

  public static Value of(double value) {
    return new Number(value);
  }

  public static Value of(String value) {
    return new StringValue(value);
  }

  public static Value of(boolean value) {
    return new BooleanValue(value);
  }

  public static List of(java.util.List<Value> values) {
    return new List(values);
  }

  public static Struct of(Map<String, Value> values) {
    return new Struct(values);
  }

  static Value fromProto(com.google.protobuf.Value protoValue) {
    final com.google.protobuf.Value.KindCase kind = protoValue.getKindCase();
    switch (kind) {
      case BOOL_VALUE:
        return Value.of(protoValue.getBoolValue());
      case NUMBER_VALUE:
        return Value.of(protoValue.getNumberValue());
      case STRING_VALUE:
        return Value.of(protoValue.getStringValue());
      case NULL_VALUE:
        return NULL_VALUE;
      case STRUCT_VALUE:
        return Struct.fromProto(protoValue.getStructValue());
      case LIST_VALUE:
        final java.util.List<Value> list =
            protoValue.getListValue().getValuesList().stream()
                .map(Value::fromProto)
                .collect(Collectors.toList());
        return new List(list);
    }
    throw new IllegalArgumentException("Unsupported value kind:" + kind);
  }

  abstract com.google.protobuf.Value toProto();

  public static class StringValue extends Value {
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
    com.google.protobuf.Value toProto() {
      return com.google.protobuf.Value.newBuilder().setStringValue(value).build();
    }

    @Override
    public String toString() {
      return value;
    }
  }

  public static class BooleanValue extends Value {
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
    com.google.protobuf.Value toProto() {
      return com.google.protobuf.Value.newBuilder().setBoolValue(value).build();
    }
  }

  public static class Number extends Value {

    private final double value;

    private Number(double value) {
      this.value = value;
    }

    @Override
    public boolean isNumber() {
      return true;
    }

    @Override
    public double asNumber() {
      return value;
    }

    @Override
    public String toString() {
      return String.valueOf(value);
    }

    @Override
    com.google.protobuf.Value toProto() {
      return com.google.protobuf.Value.newBuilder().setNumberValue(value).build();
    }
  }

  public static class List extends Value {
    private final ImmutableList<Value> values;

    public List(java.util.List<Value> values) {
      this.values = ImmutableList.copyOf(values);
    }

    @Override
    public boolean isList() {
      return true;
    }

    @Override
    public java.util.List<Value> asList() {
      return ImmutableList.copyOf(values);
    }

    @Override
    public String toString() {
      return "[" + values + "]";
    }

    @Override
    com.google.protobuf.Value toProto() {
      final ListValue value =
          ListValue.newBuilder()
              .addAllValues(values.stream().map(Value::toProto).collect(Collectors.toList()))
              .build();
      return com.google.protobuf.Value.newBuilder().setListValue(value).build();
    }

    static List fromProto(ListValue list) {
      return new List(
          list.getValuesList().stream().map(Value::fromProto).collect(Collectors.toList()));
    }
  }

  public static class Struct extends Value {
    static final Struct EMPTY = new Struct(ImmutableMap.of());
    private final ImmutableMap<String, Value> values;

    protected Struct(Map<String, Value> values) {
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

    public Value get(String... path) {
      Value value = this;
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
    com.google.protobuf.Value toProto() {
      final com.google.protobuf.Struct.Builder builder = com.google.protobuf.Struct.newBuilder();
      values.forEach((key, value) -> builder.putFields(key, value.toProto()));
      return com.google.protobuf.Value.newBuilder().setStructValue(builder).build();
    }

    static Struct fromProto(com.google.protobuf.Struct struct) {
      return new Struct(Maps.transformValues(struct.getFieldsMap(), Value::fromProto));
    }

    public Map<String, Value> asMap() {
      return values;
    }

    public Map<String, com.google.protobuf.Value> asProtoMap() {
      return values.entrySet().stream()
          .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().toProto()));
    }

    public static final class Builder {

      private final ImmutableMap.Builder<String, Value> builder = ImmutableMap.builder();

      public Builder set(String key, Value value) {
        if (!value.isNull()) builder.put(key, value);
        return this;
      }

      public Builder set(String key, double value) {
        return set(key, Value.of(value));
      }

      public Builder set(String key, String value) {
        return set(key, Value.of(value));
      }

      public Builder set(String key, boolean value) {
        return set(key, Value.of(value));
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
    return toProto().equals(((Value) obj).toProto());
  }
}
