package com.spotify.confidence;

import com.spotify.confidence.events.v1.Event;
import java.io.IOException;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

public class FakeUploader implements EventUploader {
  private final List<Integer> failAtUploadWithIndex;
  private final AtomicInteger uploadCount = new AtomicInteger();
  public Queue<List<Event>> uploadCalls = new ConcurrentLinkedQueue<>();

  public FakeUploader() {
    this.failAtUploadWithIndex = List.of();
  }

  public FakeUploader(List<Integer> failAtUploadWithIndex) {
    this.failAtUploadWithIndex = failAtUploadWithIndex;
  }

  @Override
  public CompletableFuture<Boolean> upload(List<Event> events) {
    uploadCalls.add(events);
    final int uploadCount = this.uploadCount.incrementAndGet();
    if (failAtUploadWithIndex.contains(uploadCount)) {
      return CompletableFuture.completedFuture(false);
    }
    return CompletableFuture.completedFuture(true);
  }

  @Override
  public void close() throws IOException {
    // no-op
  }

  public void reset() {
    uploadCount.set(0);
    uploadCalls.clear();
  }
}
