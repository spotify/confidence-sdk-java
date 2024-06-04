package com.spotify.confidence;

import java.time.Instant;

public class FakeClock implements Clock {
  private long currentTimeSeconds;

  public void setCurrentTimeSeconds(long currentTimeSeconds) {
    this.currentTimeSeconds = currentTimeSeconds;
  }

  @Override
  public Instant get() {
    return Instant.ofEpochSecond(currentTimeSeconds);
  }
}
