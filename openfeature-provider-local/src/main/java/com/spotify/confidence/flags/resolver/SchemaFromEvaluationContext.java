package com.spotify.confidence.flags.resolver;

import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import com.spotify.confidence.shaded.flags.admin.v1.ContextFieldSemanticType;
import com.spotify.confidence.shaded.flags.admin.v1.EvaluationContextSchemaField;
import com.spotify.confidence.targeting.value.SemanticVersion;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class SchemaFromEvaluationContext {
  private static final int MIN_DATE_LENGTH = "2025-04-01".length();
  private static final int MIN_TIMESTAMP_LENGTH = "2025-04-01T0000".length();
  private static final Set<String> COUNTRY_CODES =
      Locale.getISOCountries(Locale.IsoCountryCode.PART1_ALPHA2);

  public record DerivedClientSchema(
      Map<String, EvaluationContextSchemaField.Kind> fields,
      Map<String, ContextFieldSemanticType> semanticTypes) {}

  public static DerivedClientSchema getSchema(Struct evaluationContext) {
    final Map<String, EvaluationContextSchemaField.Kind> flatSchema = new HashMap<>();
    final Map<String, ContextFieldSemanticType> semanticTypes = new HashMap<>();
    flattenedSchema(evaluationContext, "", flatSchema, semanticTypes);
    return new DerivedClientSchema(flatSchema, semanticTypes);
  }

  private static void flattenedSchema(
      Struct struct,
      String fieldPath,
      Map<String, EvaluationContextSchemaField.Kind> flatSchema,
      Map<String, ContextFieldSemanticType> semanticTypes) {
    struct
        .getFieldsMap()
        .forEach(
            (field, value) -> {
              if (value.getKindCase() == Value.KindCase.STRUCT_VALUE) {
                flattenedSchema(
                    value.getStructValue(), fieldPath + field + ".", flatSchema, semanticTypes);
              } else {
                addFieldSchema(value, fieldPath + field, flatSchema, semanticTypes);
              }
            });
  }

  private static void addFieldSchema(
      Value value,
      String fieldPath,
      Map<String, EvaluationContextSchemaField.Kind> flatSchema,
      Map<String, ContextFieldSemanticType> semanticTypes) {
    final var kind = value.getKindCase();
    if (kind == Value.KindCase.STRING_VALUE) {
      flatSchema.put(fieldPath, EvaluationContextSchemaField.Kind.STRING_KIND);
      guessSemanticType(value.getStringValue(), fieldPath, semanticTypes);
    } else if (kind == Value.KindCase.BOOL_VALUE) {
      flatSchema.put(fieldPath, EvaluationContextSchemaField.Kind.BOOL_KIND);
    } else if (kind == Value.KindCase.NUMBER_VALUE) {
      flatSchema.put(fieldPath, EvaluationContextSchemaField.Kind.NUMBER_KIND);
    } else if (kind == Value.KindCase.NULL_VALUE) {
      flatSchema.put(fieldPath, EvaluationContextSchemaField.Kind.NULL_KIND);
    } else if (kind == Value.KindCase.LIST_VALUE) {
      final var subKinds =
          value.getListValue().getValuesList().stream()
              .map(Value::getKindCase)
              .collect(Collectors.toSet());
      if (subKinds.size() == 1) {
        addFieldSchema(value.getListValue().getValues(0), fieldPath, flatSchema, semanticTypes);
      }
    }
  }

  private static void guessSemanticType(
      String value, String fieldPath, Map<String, ContextFieldSemanticType> semanticTypes) {
    final String lowerCasePath = fieldPath.toLowerCase(Locale.ROOT);
    if (lowerCasePath.contains("country")) {
      if (COUNTRY_CODES.contains(value.toUpperCase())) {
        semanticTypes.put(
            fieldPath,
            ContextFieldSemanticType.newBuilder()
                .setCountry(
                    ContextFieldSemanticType.CountrySemanticType.newBuilder()
                        .setFormat(
                            ContextFieldSemanticType.CountrySemanticType.CountryFormat
                                .TWO_LETTER_ISO_CODE)
                        .build())
                .build());
      }
    } else if (isDate(value)) {
      semanticTypes.put(
          fieldPath,
          ContextFieldSemanticType.newBuilder()
              .setDate(ContextFieldSemanticType.DateSemanticType.getDefaultInstance())
              .build());
    } else if (isTimestamp(value)) {
      semanticTypes.put(
          fieldPath,
          ContextFieldSemanticType.newBuilder()
              .setTimestamp(ContextFieldSemanticType.TimestampSemanticType.getDefaultInstance())
              .build());
    } else if (isSemanticVersion(value)) {
      semanticTypes.put(
          fieldPath,
          ContextFieldSemanticType.newBuilder()
              .setVersion(ContextFieldSemanticType.VersionSemanticType.getDefaultInstance())
              .build());
    }
  }

  private static boolean isSemanticVersion(String value) {
    return SemanticVersion.isValid(value);
  }

  private static boolean isTimestamp(String value) {
    if (value.length() < MIN_TIMESTAMP_LENGTH) {
      return false;
    }
    return EvalUtil.parseInstant(value) != null;
  }

  private static boolean isDate(String value) {
    if (value.length() < MIN_DATE_LENGTH) {
      return false;
    }
    try {
      LocalDate.parse(value);
      return true;
    } catch (DateTimeParseException ex) {
      return false;
    }
  }
}
