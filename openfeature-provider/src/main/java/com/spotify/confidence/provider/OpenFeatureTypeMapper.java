package com.spotify.confidence.provider;

import com.google.protobuf.Struct;
import com.google.protobuf.util.Values;
import com.spotify.confidence.shaded.flags.types.v1.FlagSchema;
import com.spotify.confidence.shaded.flags.types.v1.FlagSchema.SchemaTypeCase;
import com.spotify.confidence.shaded.flags.types.v1.FlagSchema.StructFlagSchema;
import dev.openfeature.sdk.MutableStructure;
import dev.openfeature.sdk.Structure;
import dev.openfeature.sdk.Value;
import dev.openfeature.sdk.exceptions.ParseError;
import dev.openfeature.sdk.exceptions.ValueNotConvertableError;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// For now, only package visibility to keep control on this part of the code
class OpenFeatureTypeMapper {

  private static Value from(com.google.protobuf.Value value, FlagSchema schema) {
    if (schema.getSchemaTypeCase() == SchemaTypeCase.SCHEMATYPE_NOT_SET) {
      throw new ParseError("schemaType not set in FlagSchema");
    }

    final String mismatchPrefix = "Mismatch between schema and value:";
    switch (value.getKindCase()) {
      case NULL_VALUE:
        try {
          return new Value((Object) null);
        } catch (InstantiationException e) {
          throw new RuntimeException(e);
        }
      case NUMBER_VALUE:
        switch (schema.getSchemaTypeCase()) {
          case INT_SCHEMA:
            final int intVal = (int) value.getNumberValue();
            if (intVal != value.getNumberValue()) {
              throw new ParseError(
                  String.format(
                      "%s value should be an int, but it is a double/long", mismatchPrefix));
            }
            return new Value(intVal);
          case DOUBLE_SCHEMA:
            return new Value(value.getNumberValue());
          default:
            throw new ParseError("Number field must have schema type int or double");
        }
      case STRING_VALUE:
        if (schema.getSchemaTypeCase() != SchemaTypeCase.STRING_SCHEMA) {
          throw new ParseError(
              String.format(
                  "%s value is a String, but it should be something else", mismatchPrefix));
        }
        return new Value(value.getStringValue());
      case BOOL_VALUE:
        if (schema.getSchemaTypeCase() != SchemaTypeCase.BOOL_SCHEMA) {
          throw new ParseError(
              String.format("%s value is a bool, but should be something else", mismatchPrefix));
        }
        return new Value(value.getBoolValue());
      case STRUCT_VALUE:
        if (schema.getSchemaTypeCase() != SchemaTypeCase.STRUCT_SCHEMA) {
          throw new ParseError(
              String.format("%s value is a struct, but should be something else", mismatchPrefix));
        }

        return from(value.getStructValue(), schema.getStructSchema());
      case LIST_VALUE:
        if (schema.getSchemaTypeCase() != SchemaTypeCase.LIST_SCHEMA) {
          throw new ParseError(
              String.format("%s value is a list, but should be something else", mismatchPrefix));
        }

        final List<Value> mappedList =
            value.getListValue().getValuesList().stream()
                .map(val -> from(val, schema.getListSchema().getElementSchema()))
                .collect(Collectors.toList());
        return new Value(mappedList);
      case KIND_NOT_SET:
        throw new ParseError("kind not set in com.google.protobuf.Value");
      default:
        throw new ParseError("Unknown value type");
    }
  }

  public static Value from(Struct struct, StructFlagSchema schema) {
    final Map<String, Value> map =
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

    return new Value(new MutableStructure(map));
  }

  public static com.google.protobuf.Value from(Value val) {
    if (val.isNumber()) {
      return Values.of(val.asDouble());
    } else if (val.isBoolean()) {
      return Values.of(val.asBoolean());
    } else if (val.isNull()) {
      return Values.ofNull();
    } else if (val.isInstant()) {
      throw new ValueNotConvertableError("Converting Instant Value is currently not supported");
    } else if (val.isString()) {
      return Values.of(val.asString());
    } else if (val.isList()) {
      final List<Value> values = val.asList();
      return Values.of(
          values.stream().map(OpenFeatureTypeMapper::from).collect(Collectors.toList()));
    } else if (val.isStructure()) {
      final Structure structure = val.asStructure();
      final Map<String, com.google.protobuf.Value> protoMap =
          structure.asMap().keySet().stream()
              .collect(Collectors.toMap(key -> key, key -> from(structure.getValue(key))));
      return Values.of(Struct.newBuilder().putAllFields(protoMap).build());
    }
    throw new ValueNotConvertableError("Unknown Value type");
  }
}
