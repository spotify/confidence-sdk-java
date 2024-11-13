package com.spotify.confidence;

import com.google.protobuf.Struct;
import com.spotify.confidence.Exceptions.IllegalValueType;
import com.spotify.confidence.Exceptions.IncompatibleValueType;
import com.spotify.confidence.Exceptions.ParseError;
import com.spotify.confidence.shaded.flags.types.v1.FlagSchema;
import com.spotify.confidence.shaded.flags.types.v1.FlagSchema.SchemaTypeCase;
import com.spotify.confidence.shaded.flags.types.v1.FlagSchema.StructFlagSchema;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

class ConfidenceTypeMapper {

  private static ConfidenceValue from(com.google.protobuf.Value value, FlagSchema schema)
      throws ParseError {
    if (schema.getSchemaTypeCase() == SchemaTypeCase.SCHEMATYPE_NOT_SET) {
      throw new ParseError("schemaType not set in FlagSchema");
    }

    final String mismatchPrefix = "Mismatch between schema and value:";
    switch (value.getKindCase()) {
      case NULL_VALUE:
        return ConfidenceValue.NULL_VALUE;
      case NUMBER_VALUE:
        switch (schema.getSchemaTypeCase()) {
          case INT_SCHEMA:
            final int intVal = (int) value.getNumberValue();
            if (intVal != value.getNumberValue()) {
              throw new ParseError(
                  String.format(
                      "%s %s should be an int, but it is a double/long",
                      mismatchPrefix, value.getNumberValue()));
            }
            return ConfidenceValue.of(intVal);
          case DOUBLE_SCHEMA:
            return ConfidenceValue.of(value.getNumberValue());
          default:
            throw new ParseError(
                String.format(
                    "%s %s is a Number, but it should be %s",
                    mismatchPrefix, value, schema.getSchemaTypeCase()));
        }
      case STRING_VALUE:
        if (schema.getSchemaTypeCase() != SchemaTypeCase.STRING_SCHEMA) {
          throw new ParseError(
              String.format(
                  "%s %s is a String, but it should be %s",
                  mismatchPrefix, value, schema.getSchemaTypeCase()));
        }
        return ConfidenceValue.of(value.getStringValue());
      case BOOL_VALUE:
        if (schema.getSchemaTypeCase() != SchemaTypeCase.BOOL_SCHEMA) {
          throw new ParseError(
              String.format(
                  "%s %s is a Bool, but it should be %s",
                  mismatchPrefix, value, schema.getSchemaTypeCase()));
        }
        return ConfidenceValue.of(value.getBoolValue());
      case STRUCT_VALUE:
        if (schema.getSchemaTypeCase() != SchemaTypeCase.STRUCT_SCHEMA) {
          throw new ParseError(
              String.format(
                  "%s %s is a Struct, but it should be %s",
                  mismatchPrefix, value, schema.getSchemaTypeCase()));
        }
        return from(value.getStructValue(), schema.getStructSchema());
      case LIST_VALUE:
        if (schema.getSchemaTypeCase() != SchemaTypeCase.LIST_SCHEMA) {
          throw new ParseError(
              String.format(
                  "%s %s is a List, but it should be %s",
                  mismatchPrefix, value, schema.getSchemaTypeCase()));
        }
        final List<ConfidenceValue> mappedList =
            value.getListValue().getValuesList().stream()
                .map(val -> from(val, schema.getListSchema().getElementSchema()))
                .collect(Collectors.toList());
        return ConfidenceValue.of(mappedList);
      case KIND_NOT_SET:
        throw new ParseError("kind not set in com.google.protobuf.Value");
      default:
        throw new ParseError("Unknown value type");
    }
  }

  public static ConfidenceValue from(Struct struct, StructFlagSchema schema) {
    final Map<String, ConfidenceValue> map =
        struct.getFieldsMap().entrySet().stream()
            .collect(
                Collectors.toMap(
                    Map.Entry::getKey,
                    entry -> {
                      if (schema.getSchemaMap().containsKey(entry.getKey())) {
                        return from(entry.getValue(), schema.getSchemaMap().get(entry.getKey()));
                      } else {
                        throw new ParseError(
                            String.format("Lacking schema for field '%s'", entry.getKey()));
                      }
                    }));

    return ConfidenceValue.Struct.ofMap(map);
  }

  public static <T> T getTyped(ConfidenceValue value, T defaultValue)
      throws IllegalValueType, IncompatibleValueType {
    if (value.equals(ConfidenceValue.NULL_VALUE)) {
      return defaultValue;
    }

    if (defaultValue instanceof String) {
      if (value.isString()) {
        return (T) value.asString();
      }
      throw new IncompatibleValueType(
          String.format(
              "Default type %s, but value of type %s", defaultValue.getClass(), value.getClass()));
    } else if (defaultValue instanceof Integer) {
      if (value.isInteger()) {
        return (T) java.lang.Integer.valueOf(value.asInteger());
      }
      throw new IncompatibleValueType(
          String.format(
              "Default type %s, but value of type %s", defaultValue.getClass(), value.getClass()));
    } else if (defaultValue instanceof Double) {
      if (value.isDouble()) {
        return (T) Double.valueOf(value.asDouble());
      }
      throw new IncompatibleValueType(
          String.format(
              "Default type %s, but value of type %s", defaultValue.getClass(), value.getClass()));
    } else if (defaultValue instanceof Boolean) {
      if (value.isBoolean()) {
        return (T) Boolean.valueOf(value.asBoolean());
      }
      throw new IncompatibleValueType(
          String.format(
              "Default type %s, but value of type %s", defaultValue.getClass(), value.getClass()));
    } else if (defaultValue instanceof ConfidenceValue.List) {
      if (value.isList()) {
        return (T) value.asList();
      }
      throw new IncompatibleValueType(
          String.format(
              "Default value type %s, but value of type %s",
              defaultValue.getClass(), value.getClass()));
    } else if (defaultValue instanceof ConfidenceValue.Struct) {
      if (value.isStruct()) {
        return (T) value.asStruct();
      }
      throw new IncompatibleValueType(
          String.format(
              "Default value type %s, but value of type %s",
              defaultValue.getClass(), value.getClass()));
    } else {
      // Unsupported default value type
      throw new IllegalValueType(String.format("Illegal value type: %s", defaultValue.getClass()));
    }
  }
}
