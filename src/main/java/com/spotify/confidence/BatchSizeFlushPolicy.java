package com.spotify.confidence;

import java.util.concurrent.atomic.AtomicInteger;

class BatchSizeFlushPolicy implements FlushPolicy {

  private final int size;

  public BatchSizeFlushPolicy(int size) {
    this.size = size;
  }

  private final AtomicInteger currentCount = new AtomicInteger(0);

  @Override
  public void hit() {
    currentCount.getAndIncrement();
  }

  @Override
  public boolean shouldFlush() {
    return currentCount.get() >= size;
  }

  @Override
  public void reset() {
    currentCount.set(0);
  }
}
