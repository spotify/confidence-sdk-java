package com.spotify.confidence;

import com.google.protobuf.Struct;
import com.google.protobuf.util.Structs;
import com.google.protobuf.util.Values;
import com.spotify.confidence.shaded.flags.resolver.v1.ResolveFlagsResponse;
import com.spotify.confidence.shaded.flags.resolver.v1.ResolveReason;
import com.spotify.confidence.shaded.flags.resolver.v1.ResolvedFlag;
import com.spotify.confidence.shaded.flags.types.v1.FlagSchema;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class ResolverClientTestUtils {

  public static class FakeFlagResolverClient implements FlagResolverClient {

    public boolean closed = false;
    private Map<String, ConfidenceValue.Struct> resolves = new HashMap<>();

    public ResolveFlagsResponse response = generateSampleResponse(Collections.emptyList());

    @Override
    public CompletableFuture<ResolveFlagsResponse> resolveFlags(
        String flag, ConfidenceValue.Struct context, String providerId) {
      resolves.put(flag, context);
      return CompletableFuture.completedFuture(response);
    }

    @Override
    public void close() {
      closed = true;
    }
  }

  static ResolveFlagsResponse generateSampleResponse(List<ValueSchemaHolder> additionalProps) {
    return ResolveFlagsResponse.newBuilder()
        .addResolvedFlags(generateResolvedFlag(additionalProps))
        .build();
  }

  static class ValueSchemaHolder {
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

  private static ResolvedFlag generateResolvedFlag(
      List<ResolverClientTestUtils.ValueSchemaHolder> additionalProps) {
    final Struct.Builder valueBuilder =
        Struct.newBuilder()
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

  private static FlagSchema.Builder getSchemaBuilder(
      ResolverClientTestUtils.ValueSchemaHolder valueSchemaHolder) {
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
