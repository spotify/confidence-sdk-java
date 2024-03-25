package com.spotify.confidence;

import java.util.ArrayList;
import java.util.List;

public final class EventSenderTestUtils {

  public EventSenderTestUtils() {}

  static List<FlushPolicy> getFlushPolicies(int minInterval, int minSize) {
    final List<FlushPolicy> flushPolicyList = new ArrayList<>();
    final FlushPolicy sizeFlushPolicy = new BatchSizeFlushPolicy(minSize);

    final FlushPolicy intervalFlushPolicy =
        new FlushPolicy() {

          long lastFlush = System.currentTimeMillis();

          @Override
          public void hit() {}

          @Override
          public boolean shouldFlush() {
            final long currentTime = System.currentTimeMillis();
            return currentTime - lastFlush > minInterval;
          }

          @Override
          public void reset() {
            lastFlush = System.currentTimeMillis();
          }
        };
    flushPolicyList.add(sizeFlushPolicy);
    flushPolicyList.add(intervalFlushPolicy);
    return flushPolicyList;
  }
}
