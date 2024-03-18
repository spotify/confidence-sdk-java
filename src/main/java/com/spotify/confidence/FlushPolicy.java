package com.spotify.confidence;

public interface FlushPolicy {
  void hit();

  boolean shouldFlush();

  void reset();
}
