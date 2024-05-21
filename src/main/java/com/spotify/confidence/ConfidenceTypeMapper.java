package com.spotify.confidence;

import com.google.protobuf.Struct;
import com.spotify.confidence.shaded.flags.types.v1.FlagSchema;
import com.spotify.confidence.shaded.flags.types.v1.FlagSchema.SchemaTypeCase;
import com.spotify.confidence.shaded.flags.types.v1.FlagSchema.StructFlagSchema;
import dev.openfeature.sdk.exceptions.ParseError;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

class ConfidenceTypeMapper {

  private static ConfidenceValue from(com.google.protobuf.Value value, FlagSchema schema) {
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
                      "%s value should be an int, but it is a double/long", mismatchPrefix));
            }
            return ConfidenceValue.of(intVal);
          case DOUBLE_SCHEMA:
            return ConfidenceValue.of(value.getNumberValue());
          default:
            throw new ParseError("Number field must have schema type int or double");
        }
      case STRING_VALUE:
        if (schema.getSchemaTypeCase() != SchemaTypeCase.STRING_SCHEMA) {
          throw new ParseError(
              String.format(
                  "%s value is a String, but it should be something else", mismatchPrefix));
        }
        return ConfidenceValue.of(value.getStringValue());
      case BOOL_VALUE:
        if (schema.getSchemaTypeCase() != SchemaTypeCase.BOOL_SCHEMA) {
          throw new ParseError(
              String.format("%s value is a bool, but should be something else", mismatchPrefix));
        }
        return ConfidenceValue.of(value.getBoolValue());
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
}
