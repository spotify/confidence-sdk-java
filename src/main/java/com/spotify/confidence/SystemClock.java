package com.spotify.confidence;

import java.time.Instant;

class SystemClock implements Clock {
  @Override
  public long currentTimeSeconds() {
    return Instant.now().getEpochSecond();
  }
}
