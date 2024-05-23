package com.spotify.confidence.common;

import com.google.protobuf.util.Structs;
import com.google.protobuf.util.Values;
import com.spotify.confidence.common.ConfidenceValue.Struct;
import com.spotify.confidence.common.Exceptions.IllegalValuePath;
import com.spotify.confidence.common.Exceptions.ValueNotFound;
import com.spotify.confidence.shaded.flags.resolver.v1.ResolveFlagsResponse;
import com.spotify.confidence.shaded.flags.resolver.v1.ResolveReason;
import com.spotify.confidence.shaded.flags.resolver.v1.ResolvedFlag;
import com.spotify.confidence.shaded.flags.types.v1.FlagSchema;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;
import org.slf4j.Logger;

public final class ConfidenceUtils {

  private ConfidenceUtils() {}

  private static final Logger log = org.slf4j.LoggerFactory.getLogger(ConfidenceUtils.class);

  public static ConfidenceValue getValueForPath(List<String> path, ConfidenceValue fullValue)
      throws ValueNotFound {
    ConfidenceValue value = fullValue;
    for (String fieldName : path) {
      final Struct structure = value.asStruct();
      if (structure == null) {
        // value's inner object actually is no structure
        log.warn(
            "Illegal attempt to derive field '{}' on non-structure value '{}'", fieldName, value);
        throw new ValueNotFound(
            String.format(
                "Illegal attempt to derive field '%s' on non-structure value '%s'",
                fieldName, value));
      }

      value = structure.get(fieldName);

      if (value == null) {
        // we know that null indicates absence of a proper value because intended nulls would be an
        // instance of type Value
        log.warn(
            "Illegal attempt to derive non-existing field '{}' on structure value '{}'",
            fieldName,
            structure);
        throw new ValueNotFound(
            String.format(
                "Illegal attempt to derive non-existing field '%s' on structure value '%s'",
                fieldName, structure));
      }
    }

    return value;
  }

  public static class FlagPath {
    private final String flag;
    private final List<String> path;

    public FlagPath(String flag, List<String> path) {
      this.flag = flag;
      this.path = path;
    }

    public String getFlag() {
      return flag;
    }

    public List<String> getPath() {
      return path;
    }

    public static FlagPath getPath(String str) throws IllegalValuePath {
      final String regex = Pattern.quote(".");
      final String[] parts = str.split(regex);

      if (parts.length == 0) {
        // this happens for malformed corner cases such as: str = "..."
        log.warn("Illegal path string '{}'", str);
        throw new IllegalValuePath(String.format("Illegal path string '%s'", str));
      } else if (parts.length == 1) {
        // str doesn't contain the delimiter
        return new FlagPath(str, List.of());
      } else {
        return new FlagPath(parts[0], Arrays.asList(parts).subList(1, parts.length));
      }
    }
  }

  public static String getSdkVersion() {
    try {
      final Properties prop = new Properties();
      prop.load(ConfidenceUtils.class.getResourceAsStream("/version.properties"));
      return prop.getProperty("version");
    } catch (IOException e) {
      throw new RuntimeException("Can't determine version of the SDK", e);
    }
  }

  public static class ResolverClientTestUtils {

    public static class FakeFlagResolverClient implements FlagResolverClient {

      public boolean closed = false;
      private Map<String, Struct> resolves = new HashMap<>();

      public ResolveFlagsResponse response = generateSampleResponse(Collections.emptyList());

      @Override
      public CompletableFuture<ResolveFlagsResponse> resolveFlags(String flag, Struct context) {
        resolves.put(flag, context);
        return CompletableFuture.completedFuture(response);
      }

      @Override
      public void close() {
        closed = true;
      }
    }

    public static ResolveFlagsResponse generateSampleResponse(
        List<ValueSchemaHolder> additionalProps) {
      return ResolveFlagsResponse.newBuilder()
          .addResolvedFlags(generateResolvedFlag(additionalProps))
          .build();
    }

    public static class ValueSchemaHolder {
      public ValueSchemaHolder(
          String prop, com.google.protobuf.Value value, FlagSchema.SchemaTypeCase schemaTypeCase) {
        this.prop = prop;
        this.value = value;
        this.schemaTypeCase = schemaTypeCase;
      }

      String prop;
      com.google.protobuf.Value value;
      FlagSchema.SchemaTypeCase schemaTypeCase;
    }

    private static ResolvedFlag generateResolvedFlag(List<ValueSchemaHolder> additionalProps) {
      final com.google.protobuf.Struct.Builder valueBuilder =
          com.google.protobuf.Struct.newBuilder()
              .putAllFields(
                  Map.of(
                      "prop-A",
                      Values.of(false),
                      "prop-B",
                      Values.of(
                          Structs.of(
                              "prop-C", Values.of("str-val"),
                              "prop-D", Values.of(5.3))),
                      "prop-E",
                      Values.of(50),
                      "prop-F",
                      Values.of(List.of(Values.of("a"), Values.of("b"))),
                      "prop-G",
                      Values.of(
                          Structs.of(
                              "prop-H", Values.ofNull(),
                              "prop-I", Values.ofNull()))));

      final FlagSchema.StructFlagSchema.Builder schemaBuilder =
          FlagSchema.StructFlagSchema.newBuilder()
              .putAllSchema(
                  Map.of(
                      "prop-A",
                      FlagSchema.newBuilder()
                          .setBoolSchema(FlagSchema.BoolFlagSchema.getDefaultInstance())
                          .build(),
                      "prop-B",
                      FlagSchema.newBuilder()
                          .setStructSchema(
                              FlagSchema.StructFlagSchema.newBuilder()
                                  .putAllSchema(
                                      Map.of(
                                          "prop-C",
                                          FlagSchema.newBuilder()
                                              .setStringSchema(
                                                  FlagSchema.StringFlagSchema.getDefaultInstance())
                                              .build(),
                                          "prop-D",
                                          FlagSchema.newBuilder()
                                              .setDoubleSchema(
                                                  FlagSchema.DoubleFlagSchema.getDefaultInstance())
                                              .build()))
                                  .build())
                          .build(),
                      "prop-E",
                      FlagSchema.newBuilder()
                          .setIntSchema(FlagSchema.IntFlagSchema.getDefaultInstance())
                          .build(),
                      "prop-F",
                      FlagSchema.newBuilder()
                          .setListSchema(
                              FlagSchema.ListFlagSchema.newBuilder()
                                  .setElementSchema(
                                      FlagSchema.newBuilder()
                                          .setStringSchema(
                                              FlagSchema.StringFlagSchema.getDefaultInstance())
                                          .build())
                                  .build())
                          .build(),
                      "prop-G",
                      FlagSchema.newBuilder()
                          .setStructSchema(
                              FlagSchema.StructFlagSchema.newBuilder()
                                  .putAllSchema(
                                      Map.of(
                                          "prop-H",
                                          FlagSchema.newBuilder()
                                              .setStringSchema(
                                                  FlagSchema.StringFlagSchema.getDefaultInstance())
                                              .build(),
                                          "prop-I",
                                          FlagSchema.newBuilder()
                                              .setIntSchema(
                                                  FlagSchema.IntFlagSchema.getDefaultInstance())
                                              .build()))
                                  .build())
                          .build()));

      additionalProps.forEach(
          (valueSchemaHolder) -> {
            valueBuilder.putFields(valueSchemaHolder.prop, valueSchemaHolder.value);
            final FlagSchema.Builder builder = getSchemaBuilder(valueSchemaHolder);
            schemaBuilder.putSchema(valueSchemaHolder.prop, builder.build());
          });

      return ResolvedFlag.newBuilder()
          .setFlag("flags/flag")
          .setVariant("flags/flag/variants/var-A")
          .setReason(ResolveReason.RESOLVE_REASON_MATCH)
          .setValue(valueBuilder)
          .setFlagSchema(schemaBuilder)
          .build();
    }

    private static FlagSchema.Builder getSchemaBuilder(ValueSchemaHolder valueSchemaHolder) {
      final FlagSchema.Builder builder = FlagSchema.newBuilder();
      switch (valueSchemaHolder.schemaTypeCase) {
        case STRUCT_SCHEMA:
          builder.setStructSchema(FlagSchema.StructFlagSchema.getDefaultInstance());
          break;
        case LIST_SCHEMA:
          builder.setListSchema(FlagSchema.ListFlagSchema.getDefaultInstance());
          break;
        case INT_SCHEMA:
          builder.setIntSchema(FlagSchema.IntFlagSchema.getDefaultInstance());
          break;
        case DOUBLE_SCHEMA:
          builder.setDoubleSchema(FlagSchema.DoubleFlagSchema.getDefaultInstance());
          break;
        case STRING_SCHEMA:
          builder.setStringSchema(FlagSchema.StringFlagSchema.getDefaultInstance());
          break;
        case BOOL_SCHEMA:
          builder.setBoolSchema(FlagSchema.BoolFlagSchema.getDefaultInstance());
          break;
        case SCHEMATYPE_NOT_SET:
          break;
      }
      return builder;
    }
  }
}
