package com.spotify.confidence.eventsender;

import static com.spotify.confidence.eventsender.EventSenderTestUtils.getFlushPolicies;

import com.google.common.collect.ImmutableMap;
import java.io.IOException;
import org.junit.jupiter.api.Test;

class EventSenderEngineImplTest {

  @Test
  public void printStepsTemporaryTest() {
    try (EventSenderEngine engine = new EventSenderEngineImpl(getFlushPolicies())) {
      int size = 0;
      while (size++ < 12) {
        engine.send("event-" + size, ConfidenceValue.of(ImmutableMap.of()));
        Thread.sleep(10);
      }
    } catch (InterruptedException | IOException e) {
      throw new RuntimeException(e);
    }
  }
}
