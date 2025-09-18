package com.spotify.confidence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.spotify.confidence.shaded.flags.resolver.v1.ResolveReason;
import dev.openfeature.sdk.ImmutableContext;
import dev.openfeature.sdk.ProviderEvaluation;
import dev.openfeature.sdk.Value;
import org.junit.jupiter.api.Test;

public class WasmResolveTest extends ResolveTest {
  public WasmResolveTest() {
    super(true);
  }

  @Test
  public void testAccountStateProviderInterface() {
    final AccountStateProvider customProvider = () -> exampleState.toProto().toByteArray();
    final OpenFeatureLocalResolveProvider localResolveProvider =
        new OpenFeatureLocalResolveProvider(customProvider, TestBase.secret.getSecret());
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
}
