package com.spotify.confidence.flags.resolver;

import static com.spotify.confidence.shaded.flags.types.v1.Targeting.Value.ValueCase.BOOL_VALUE;
import static com.spotify.confidence.targeting.Targetings.boolValue;
import static com.spotify.confidence.targeting.Targetings.numberValue;
import static com.spotify.confidence.targeting.Targetings.semverValue;
import static com.spotify.confidence.targeting.Targetings.stringValue;
import static java.lang.Boolean.parseBoolean;
import static java.lang.Double.parseDouble;

import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import com.google.protobuf.util.Timestamps;
import com.google.protobuf.util.Values;
import com.spotify.confidence.shaded.flags.types.v1.FlagSchema;
import com.spotify.confidence.shaded.flags.types.v1.Targeting;
import com.spotify.confidence.shaded.flags.types.v1.Targeting.ListValue;
import com.spotify.confidence.shaded.flags.types.v1.Targeting.Value.ValueCase;
import com.spotify.confidence.targeting.Util;
import io.grpc.Status;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;

public final class EvalUtil {

  private EvalUtil() {}

  public static Value getAttributeValue(Struct struct, String attributeName) {
    final String[] attributePath = attributeName.split("\\.");
    return getAttributeValue(struct, attributePath, 0);
  }

  private static Value getAttributeValue(Struct struct, String[] attributePath, int pos) {
    final Map<String, Value> fieldsMap = struct.getFieldsMap();
    final String fieldName = attributePath[pos];
    if (!fieldsMap.containsKey(fieldName)) {
      return Values.ofNull();
    }

    final Value value = fieldsMap.get(fieldName);
    if (pos == attributePath.length - 1) {
      return value;
    } else if (value.hasStructValue()) {
      return getAttributeValue(value.getStructValue(), attributePath, pos + 1);
    }

    // non-struct value addressed with .-operator
    return Values.ofNull();
  }

  /**
   * Tries to parse the a {@link Value} to an expected targeting type value. If the expected type is
   * not set, or there is no meaningful conversion to the expected type, this function will return
   * null. The null value should be treated as not matching anything further down in the evaluation
   * logic.
   */
  public static Targeting.Value convertToTargetingValue(
      Value value, Targeting.Value.ValueCase expectedType) {
    return switch (value.getKindCase()) {
      case NULL_VALUE -> {
        if (expectedType == ValueCase.VALUE_NOT_SET) {
          yield Targeting.Value.getDefaultInstance();
        }
        yield null;
      }
      case NUMBER_VALUE -> convertNumber(value.getNumberValue(), expectedType);
      case STRING_VALUE -> convertString(value.getStringValue(), expectedType);
      case BOOL_VALUE -> {
        if (expectedType == BOOL_VALUE) {
          yield boolValue(value.getBoolValue());
        }
        yield null;
      }
      case LIST_VALUE -> convertList(value, expectedType);
      case STRUCT_VALUE ->
          throw Status.INVALID_ARGUMENT
              .withDescription(value.getKindCase() + " values not supported for targeting")
              .asRuntimeException();
      default -> throw new IllegalStateException("Unexpected value: " + value.getKindCase());
    };
  }

  private static Targeting.Value convertList(Value value, ValueCase expectedType) {
    final List<Targeting.Value> convertedValues =
        value.getListValue().getValuesList().stream()
            .map(v -> convertToTargetingValue(v, expectedType))
            .toList();
    return Targeting.Value.newBuilder()
        .setListValue(ListValue.newBuilder().addAllValues(convertedValues).build())
        .build();
  }

  private static Targeting.Value convertNumber(
      double value, Targeting.Value.ValueCase expectedType) {
    return switch (expectedType) {
      case NUMBER_VALUE -> numberValue(value);
      case STRING_VALUE -> stringValue(Double.toString(value));
      default -> null;
    };
  }

  private static Targeting.Value convertString(
      String value, Targeting.Value.ValueCase expectedType) {
    return switch (expectedType) {
      case BOOL_VALUE -> boolValue(parseBoolean(value));
      case NUMBER_VALUE -> numberValue(parseDouble(value));
      case STRING_VALUE -> stringValue(value);
      case TIMESTAMP_VALUE -> timestampValue(parseInstant(value));
      case VERSION_VALUE -> semverValue(value);
      case LIST_VALUE, VALUE_NOT_SET -> null;
    };
  }

  public static Instant parseInstant(String value) {
    if (value.isEmpty()) {
      return null;
    }
    try {
      if (value.contains("T")) {
        final int tpos = value.indexOf('T');
        if (value.endsWith("Z")
            || value.substring(tpos).contains("+")
            || value.substring(tpos).contains("-")) {
          return ZonedDateTime.parse(value).toInstant();
        } else {
          return LocalDateTime.parse(value).toInstant(ZoneOffset.UTC);
        }
      } else {
        return LocalDate.parse(value).atStartOfDay(ZoneOffset.UTC).toInstant();
      }
    } catch (DateTimeParseException exception) {
      return null;
    }
  }

  public static Targeting.Value timestampValue(final Instant instant) {
    if (instant == null) {
      return null;
    }
    return Targeting.Value.newBuilder()
        .setTimestampValue(Timestamps.fromMillis(instant.toEpochMilli()))
        .build();
  }

  public static boolean isInRange(Targeting.RangeRule rangeRule, Targeting.Value value) {
    if (value == null) {
      return false;
    }

    try {
      return Util.hasOverlap(value, rangeRule);
    } catch (IllegalArgumentException e) {
      // the supplied value type might not match the type in the rule
      return false;
    }
  }

  public static Struct expandToSchema(Struct struct, FlagSchema.StructFlagSchema schema) {
    final Struct.Builder builder = struct.toBuilder();
    for (Map.Entry<String, FlagSchema> schemaEntry : schema.getSchemaMap().entrySet()) {
      final String fieldName = schemaEntry.getKey();
      final Value fieldValue = builder.getFieldsOrDefault(fieldName, null);
      final FlagSchema fieldSchema = schemaEntry.getValue();
      builder.putFields(fieldName, expandToSchema(fieldValue, fieldSchema));
    }
    return builder.build();
  }

  private static Value expandToSchema(Value value, FlagSchema schema) {
    return switch (schema.getSchemaTypeCase()) {
      case STRUCT_SCHEMA -> {
        final Struct structValue =
            value != null ? value.getStructValue() : Struct.getDefaultInstance();
        yield Values.of(expandToSchema(structValue, schema.getStructSchema()));
      }

      case LIST_SCHEMA -> {
        final List<Value> listValues =
            value != null ? value.getListValue().getValuesList() : List.of();
        yield Values.of(
            listValues.stream()
                .map(v -> expandToSchema(v, schema.getListSchema().getElementSchema()))
                .toList());
      }

      default -> value != null ? value : Values.ofNull();
    };
  }
}
