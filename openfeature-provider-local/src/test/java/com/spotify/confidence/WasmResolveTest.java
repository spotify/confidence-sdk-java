package com.spotify.confidence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.protobuf.util.Structs;
import com.google.protobuf.util.Values;
import com.spotify.confidence.shaded.flags.resolver.v1.ResolveFlagsRequest;
import com.spotify.confidence.shaded.flags.resolver.v1.ResolveFlagsResponse;
import com.spotify.confidence.shaded.flags.resolver.v1.ResolveReason;
import com.spotify.confidence.shaded.flags.resolver.v1.ResolvedFlag;
import com.spotify.confidence.shaded.flags.types.v1.FlagSchema;
import dev.openfeature.sdk.ImmutableContext;
import dev.openfeature.sdk.ProviderEvaluation;
import dev.openfeature.sdk.Value;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

public class WasmResolveTest extends ResolveTest {
  // Override desiredState to use materialization-enabled state for StickyResolveStrategy tests
  protected final ResolverState desiredState = ResolveTest.exampleStateWithMaterialization;

  public WasmResolveTest() {
    super(true);
  }

  @Test
  public void testAccountStateProviderInterface() {
    final AccountStateProvider customProvider =
        () -> ResolveTest.exampleState.toProto().toByteArray();
    final OpenFeatureLocalResolveProvider localResolveProvider =
        new OpenFeatureLocalResolveProvider(
            customProvider,
            "",
            TestBase.secret.getSecret(),
            new ResolverFallback() {
              @Override
              public CompletableFuture<ResolveFlagsResponse> resolve(ResolveFlagsRequest request) {
                return CompletableFuture.completedFuture(null);
              }

              @Override
              public void close() {}
            });
    final ProviderEvaluation<Value> objectEvaluation =
        localResolveProvider.getObjectEvaluation(
            "flag-1", new Value("error"), new ImmutableContext("user1"));

    assertEquals("flags/flag-1/variants/onnn", objectEvaluation.getVariant());
    assertEquals(ResolveReason.RESOLVE_REASON_MATCH.toString(), objectEvaluation.getReason());
    assertNull(objectEvaluation.getErrorCode());
    assertNull(objectEvaluation.getErrorMessage());
    assertTrue(objectEvaluation.getValue().isStructure());
    final var structure = objectEvaluation.getValue().asStructure();
    assertEquals("on", structure.getValue("data").asString());
    assertTrue(structure.getValue("extra").isNull());
  }

  @Test
  public void testResolverFallbackWhenMaterializationsMissing() {
    // Create a mock ResolverFallback
    // Create expected response from fallback
    final ResolveFlagsResponse expectedFallbackResponse =
        ResolveFlagsResponse.newBuilder()
            .addResolvedFlags(
                ResolvedFlag.newBuilder()
                    .setFlag("flags/flag-1")
                    .setFlagSchema(
                        FlagSchema.StructFlagSchema.newBuilder()
                            .putSchema(
                                "data",
                                FlagSchema.newBuilder()
                                    .setStringSchema(
                                        FlagSchema.StringFlagSchema.newBuilder().build())
                                    .build())
                            .build())
                    .setVariant("flags/flag-1/variants/onnn")
                    .setValue(Structs.of("data", Values.of("on")))
                    .setReason(ResolveReason.RESOLVE_REASON_MATCH)
                    .build())
            .setResolveId("fallback-resolve-id")
            .build();

    // Mock the fallback to return the expected response
    when(mockFallback.resolve(any(ResolveFlagsRequest.class)))
        .thenReturn(CompletableFuture.completedFuture(expectedFallbackResponse));

    // Create the provider with mocked fallback strategy
    final OpenFeatureLocalResolveProvider provider =
        new OpenFeatureLocalResolveProvider(
            () -> desiredState.toProto().toByteArray(), "", secret.getSecret(), mockFallback);

    // Make the resolve request using OpenFeature API
    final ProviderEvaluation<Value> evaluation =
        provider.getObjectEvaluation(
            "flag-1", new Value("default"), new ImmutableContext("test-user"));

    // Assert that we get the expected result
    assertEquals("flags/flag-1/variants/onnn", evaluation.getVariant());
    assertEquals(ResolveReason.RESOLVE_REASON_MATCH.toString(), evaluation.getReason());
    assertTrue(evaluation.getValue().isStructure());
    final var structure = evaluation.getValue().asStructure();
    assertEquals("on", structure.getValue("data").asString());
  }

  @Test
  public void testMaterializationRepositoryWhenMaterializationsMissing() {
    // Create a mock MaterializationRepository
    final MaterializationRepository mockRepository = mock(MaterializationRepository.class);

    // Create materialization info that the repository should return
    final MaterializationInfo materializationInfo =
        new MaterializationInfo(true, Map.of("MyRule", "flags/flag-1/variants/onnn"));

    final Map<String, MaterializationInfo> loadedAssignments =
        Map.of("read-mat", materializationInfo);

    // Mock the repository to return materialization info
    when(mockRepository.loadMaterializedAssignmentsForUnit(any(String.class), any()))
        .thenReturn(CompletableFuture.completedFuture(loadedAssignments));

    // Create the provider with mocked repository strategy
    final OpenFeatureLocalResolveProvider provider =
        new OpenFeatureLocalResolveProvider(
            () -> desiredState.toProto().toByteArray(), "", secret.getSecret(), mockRepository);

    // Make the resolve request using OpenFeature API
    final ProviderEvaluation<Value> evaluation =
        provider.getObjectEvaluation(
            "flag-1", new Value("default"), new ImmutableContext("test-user"));

    // Assert that we get the expected result after materialization loading
    assertEquals("flags/flag-1/variants/onnn", evaluation.getVariant());
    assertEquals(ResolveReason.RESOLVE_REASON_MATCH.toString(), evaluation.getReason());
    assertTrue(evaluation.getValue().isStructure());
    final var structure = evaluation.getValue().asStructure();
    assertEquals("on", structure.getValue("data").asString());

    // Assert that the materialization repository was called with correct input
    verify(mockRepository)
        .loadMaterializedAssignmentsForUnit(
            eq("test-user"), eq(Map.of("read-mat", List.of("MyRule"))));
  }
}
