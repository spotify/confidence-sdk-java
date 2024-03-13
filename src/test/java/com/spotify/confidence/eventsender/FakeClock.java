package com.spotify.confidence.eventsender;

public class FakeClock implements Clock {
  private long currentTimeSeconds;

  @Override
  public long currentTimeSeconds() {
    return currentTimeSeconds;
  }

  public void setCurrentTimeSeconds(long currentTimeSeconds) {
    this.currentTimeSeconds = currentTimeSeconds;
  }
}
