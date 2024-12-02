package com.spotify.confidence;

import static org.junit.jupiter.api.Assertions.*;

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
    root = Confidence.create(fakeEngine, fakeFlagResolverClient, "");
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
    child.track("Test");
  }

  @Test
  public void testCloseChildShouldNotAffectParent()
      throws IOException, ExecutionException, InterruptedException {
    final Confidence child = root.withContext(Map.of("child-key", ConfidenceValue.of("child")));
    child.close();
    root.resolveFlags("test").get();
    root.track("test", ConfidenceValue.of(Map.of("messageKey", ConfidenceValue.of("parent"))));
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
}
