package com.spotify.confidence.eventsender;

public interface FlushPolicy {
  void hit();
  boolean shouldFlush();
  void reset();
}
