package com.spotify.confidence;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ConfidenceResourceManagementTest {

  private ConfidenceInstance root;
  private FakeEventSenderEngine fakeEngine;
  private ResolverClientTestUtils.FakeFlagResolverClient fakeFlagResolverClient;

  @BeforeEach
  public void setup() {
    fakeEngine = new FakeEventSenderEngine(new FakeClock());
    fakeFlagResolverClient = new ResolverClientTestUtils.FakeFlagResolverClient();
    root = Confidence.create(fakeEngine, fakeFlagResolverClient);
  }

  @Test
  public void testCloseChildShouldNotCloseParentEngine() throws IOException {
    final EventSender child = root.withContext(Map.of("child-key", ConfidenceValue.of("child")));
    child.close();
    assertFalse(fakeEngine.closed);
    assertFalse(fakeFlagResolverClient.closed);
  }

  @Test
  public void testCloseChildShouldNotThrowFromSend() throws IOException {
    final EventSender child = root.withContext(Map.of("child-key", ConfidenceValue.of("child")));
    child.close();
    child.track("Test");
  }
}
