package com.spotify.confidence;

interface FlushPolicy {
  void hit();

  boolean shouldFlush();

  void reset();
}