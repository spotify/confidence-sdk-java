package com.spotify.confidence.eventsender;

import com.google.common.collect.ImmutableMap;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class EventSenderEngineImplTest {

  @Test
  public void test() {
    System.out.println("#################");
    List<FlushPolicy> flushPolicyList = new ArrayList<>();
    FlushPolicy sizeFlushPolicy =
        new FlushPolicy() {
          AtomicInteger size = new AtomicInteger(0);

          @Override
          public void hit() {
            size.getAndIncrement();
          }

          @Override
          public boolean shouldFlush() {
            return size.get() >= 5;
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
            return currentTime - lastFlush > 20;
          }

          @Override
          public void reset() {
            lastFlush = System.currentTimeMillis();
          }
        };
    flushPolicyList.add(sizeFlushPolicy);
    flushPolicyList.add(intervalFlushPolicy);

    EventSenderEngine engine = new EventSenderEngineImpl(flushPolicyList);
    ExecutorService executor = Executors.newFixedThreadPool(1);
    executor.submit(
        () -> {
          try {
            int size = 0;
            while (size++ < 12) {
              engine.send("sample" + size, ConfidenceValue.of(ImmutableMap.of()));
              Thread.sleep(10);
            }
            engine.shutdown();
          } catch (InterruptedException e) {
            throw new RuntimeException(e);
          }
        });
    try {
      executor.awaitTermination(1200, TimeUnit.MILLISECONDS);
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
  }
}
