package com.spotify.confidence.eventsender;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public final class EventSenderTestUtils {

  public EventSenderTestUtils() {}

  static List<FlushPolicy> getFlushPolicies(int minInterval, int minSize) {
    List<FlushPolicy> flushPolicyList = new ArrayList<>();
    FlushPolicy sizeFlushPolicy =
        new FlushPolicy() {
          final AtomicInteger size = new AtomicInteger(0);

          @Override
          public void hit() {
            size.getAndIncrement();
          }

          @Override
          public boolean shouldFlush() {
            return size.get() >= minSize;
          }

          @Override
          public void reset() {
            size.set(0);
          }
        };

    FlushPolicy intervalFlushPolicy =
        new FlushPolicy() {

          long lastFlush = System.currentTimeMillis();

          @Override
          public void hit() {}

          @Override
          public boolean shouldFlush() {
            long currentTime = System.currentTimeMillis();
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
