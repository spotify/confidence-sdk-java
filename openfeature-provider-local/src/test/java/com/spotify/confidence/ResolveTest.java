package com.spotify.confidence;

import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.protobuf.Struct;
import com.google.protobuf.util.Structs;
import com.google.protobuf.util.Values;
import com.spotify.confidence.shaded.flags.admin.v1.Flag;
import com.spotify.confidence.shaded.flags.admin.v1.Segment;
import com.spotify.confidence.shaded.flags.types.v1.FlagSchema;
import java.util.BitSet;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

abstract class ResolveTest extends TestBase {
  private static final String flag1 = "flags/flag-1";

  private static final String flagOff = flag1 + "/variants/offf";
  private static final String flagOn = flag1 + "/variants/onnn";

  private static final Flag.Variant variantOff =
      Flag.Variant.newBuilder()
          .setName(flagOff)
          .setValue(Structs.of("data", Values.of("off")))
          .build();
  private static final Flag.Variant variantOn =
      Flag.Variant.newBuilder()
          .setName(flagOn)
          .setValue(Structs.of("data", Values.of("on")))
          .build();

  private static final FlagSchema.StructFlagSchema schema1 =
      FlagSchema.StructFlagSchema.newBuilder()
          .putSchema(
              "data",
              FlagSchema.newBuilder()
                  .setStringSchema(FlagSchema.StringFlagSchema.newBuilder().build())
                  .build())
          .putSchema(
              "extra",
              FlagSchema.newBuilder()
                  .setStringSchema(FlagSchema.StringFlagSchema.newBuilder().build())
                  .build())
          .build();
  private static final String segmentA = "segments/seg-a";
  static final ResolverState exampleState;
  private static final Map<String, Flag> flags =
      Map.of(
          flag1,
          Flag.newBuilder()
              .setName(flag1)
              .setState(Flag.State.ACTIVE)
              .setSchema(schema1)
              .addVariants(variantOff)
              .addVariants(variantOn)
              .addClients(clientName)
              .addRules(
                  Flag.Rule.newBuilder()
                      .setSegment(segmentA)
                      .setEnabled(true)
                      .setAssignmentSpec(
                          Flag.Rule.AssignmentSpec.newBuilder()
                              .setBucketCount(2)
                              .addAssignments(
                                  Flag.Rule.Assignment.newBuilder()
                                      .setAssignmentId(flagOff)
                                      .setVariant(
                                          Flag.Rule.Assignment.VariantAssignment.newBuilder()
                                              .setVariant(flagOff)
                                              .build())
                                      .addBucketRanges(
                                          Flag.Rule.BucketRange.newBuilder()
                                              .setLower(0)
                                              .setUpper(1)
                                              .build())
                                      .build())
                              .addAssignments(
                                  Flag.Rule.Assignment.newBuilder()
                                      .setAssignmentId(flagOn)
                                      .setVariant(
                                          Flag.Rule.Assignment.VariantAssignment.newBuilder()
                                              .setVariant(flagOn)
                                              .build())
                                      .addBucketRanges(
                                          Flag.Rule.BucketRange.newBuilder()
                                              .setLower(1)
                                              .setUpper(2)
                                              .build())
                                      .build())
                              .build())
                      .build())
              .build());
  protected static final Map<String, Segment> segments =
      Map.of(segmentA, Segment.newBuilder().setName(segmentA).build());
  protected static final Map<String, BitSet> bitsets = Map.of(segmentA, getBitsetAllSet());

  static {
    exampleState =
        new ResolverState(
            Map.of(
                account,
                new AccountState(
                    new Account(account, Region.EU), flags, segments, bitsets, secrets, "abc")),
            secrets);
  }

  protected ResolveTest(boolean isWasm) {
    super(exampleState, isWasm);
  }

  @BeforeAll
  public static void beforeAll() {
    TestBase.setup();
  }

  @Test
  public void testInvalidSecret() {
    assertThatExceptionOfType(RuntimeException.class)
        .isThrownBy(
            () ->
                resolveWithContext(
                    List.of("flags/asd"),
                    "foo",
                    "bar",
                    Struct.newBuilder().build(),
                    false,
                    "invalid-secret"))
        .withMessage("Resolver state not set or client secret could not be found");
  }

  @Test
  public void testInvalidFlag() {
    final var response =
        resolveWithContext(List.of("flags/asd"), "foo", "bar", Struct.newBuilder().build(), false);
    assertThat(response.getResolvedFlagsList()).isEmpty();
    assertThat(response.getResolveId()).isNotEmpty();
  }

  @Test
  public void testResolveFlag() {
    final var response =
        resolveWithContext(List.of(flag1), "foo", "bar", Struct.newBuilder().build(), false);
    assertThat(response.getResolveId()).isNotEmpty();
    final Struct expectedValue =
        // expanded with nulls to match schema
        variantOn.getValue().toBuilder().putFields("extra", Values.ofNull()).build();

    assertEquals(variantOn.getName(), response.getResolvedFlags(0).getVariant());
    assertEquals(expectedValue, response.getResolvedFlags(0).getValue());
    assertEquals(schema1, response.getResolvedFlags(0).getFlagSchema());
  }

  @Test
  public void testTooLongKey() {
    assertThatExceptionOfType(RuntimeException.class)
        .isThrownBy(
            () ->
                resolveWithContext(
                    List.of(flag1),
                    RandomStringUtils.randomAlphabetic(101),
                    "bar",
                    Struct.newBuilder().build(),
                    false))
        .withMessageContaining("Targeting key is too larger, max 100 characters.");
  }
}
