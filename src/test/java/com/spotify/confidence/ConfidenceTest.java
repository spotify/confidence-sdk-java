package com.spotify.confidence;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

public class ConfidenceTest {
  private final FakeEventSenderEngine fakeEngine = new FakeEventSenderEngine(new FakeClock());
  private final ResolverClientTestUtils.FakeFlagResolverClient fakeFlagResolverClient =
      new ResolverClientTestUtils.FakeFlagResolverClient();

  @Test
  void getValue() {
    final Confidence confidence = Confidence.create(fakeEngine, fakeFlagResolverClient);
    final Integer value = confidence.getValue("flag.prop-E", 20);
    assertEquals(50, value);
  }
}
