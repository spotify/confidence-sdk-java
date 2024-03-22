package com.spotify.confidence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import dev.openfeature.sdk.*;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ConfidenceResourceManagementTest {

  private Confidence root;
  private FakeEventSenderEngine fakeEngine;
  private ResolverClientTestUtils.FakeFlagResolverClient fakeFlagResolverClient;

  @BeforeEach
  public void setup() {
    fakeEngine = new FakeEventSenderEngine(new FakeClock());
    fakeFlagResolverClient = new ResolverClientTestUtils.FakeFlagResolverClient();
    root = Confidence.create(fakeEngine, fakeFlagResolverClient);
  }

  @Test
  public void testCloseChildShouldThrowFromResolveFlags() throws IOException {
    final Confidence child = root.withContext(Map.of("child-key", ConfidenceValue.of("child")));
    child.close();
    assertThrows(IllegalStateException.class, () -> child.resolveFlags("test").get());
  }

  @Test
  public void testCloseChildShouldNotCloseParentEngine() throws IOException {
    final Confidence child = root.withContext(Map.of("child-key", ConfidenceValue.of("child")));
    child.close();
    assertFalse(fakeEngine.closed);
    assertFalse(fakeFlagResolverClient.closed);
  }

  @Test
  public void testCloseChildShouldNotThrowFromSend() throws IOException {
    final Confidence child = root.withContext(Map.of("child-key", ConfidenceValue.of("child")));
    child.close();
    child.send("Test");
  }

  @Test
  public void testCloseChildShouldNotAffectParent()
      throws IOException, ExecutionException, InterruptedException {
    final Confidence child = root.withContext(Map.of("child-key", ConfidenceValue.of("child")));
    child.close();
    root.resolveFlags("test").get();
    root.send("test", ConfidenceValue.of(Map.of("messageKey", ConfidenceValue.of("parent"))));
  }

  @Test
  public void testCloseParentShouldAffectChild() throws IOException {
    final Confidence child = root.withContext(Map.of("child-key", ConfidenceValue.of("child")));
    root.close();
    assertThrows(IllegalStateException.class, () -> child.resolveFlags("test").get());
    assertThrows(IllegalStateException.class, () -> root.resolveFlags("test").get());
    assertTrue(fakeEngine.closed);
    assertTrue(fakeFlagResolverClient.closed);
  }

  @Test
  public void testCloseChildShouldReturnDefaultsFromOpenFeatureApi() throws IOException {
    final Confidence child = root.withContext(Map.of("child-key", ConfidenceValue.of("child")));
    OpenFeatureAPI.getInstance().setProvider(new ConfidenceFeatureProvider(child));
    child.close();
    final boolean defaultValue = false;
    final FlagEvaluationDetails<Boolean> booleanDetails =
        OpenFeatureAPI.getInstance()
            .getClient()
            .getBooleanDetails(
                "some-flag",
                defaultValue,
                new ImmutableContext("some-key", Map.of("some", new Value("value"))));
    assertThat(booleanDetails.getValue()).isEqualTo(defaultValue);
    assertThat(booleanDetails.getReason()).isEqualTo(Reason.ERROR.name());
    assertThat(booleanDetails.getErrorCode()).isEqualTo(ErrorCode.GENERAL);
    assertThat(booleanDetails.getErrorMessage()).isEqualTo("Resource closed");
  }
}
