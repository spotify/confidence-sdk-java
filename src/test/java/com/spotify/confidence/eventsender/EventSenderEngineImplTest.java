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
      while (size++ < 0) {
        engine.send("sample " + size, ConfidenceValue.of(ImmutableMap.of()));
        Thread.sleep(10);
      }
      engine.close();
      engine.send("sample extra", ConfidenceValue.of(ImmutableMap.of()));
    } catch (InterruptedException | IOException e) {
      throw new RuntimeException(e);
    }
  }
}
