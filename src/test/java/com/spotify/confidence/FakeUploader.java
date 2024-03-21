package com.spotify.confidence;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class FakeUploader implements EventUploader {
  private final List<Integer> failAtUploadWithIndex;
  private int uploadCount = 0;
  public List<EventBatch> uploadCalls = new ArrayList<>();

  public FakeUploader() {
    this.failAtUploadWithIndex = List.of();
  }

  public FakeUploader(List<Integer> failAtUploadWithIndex) {
    this.failAtUploadWithIndex = failAtUploadWithIndex;
  }

  @Override
  public CompletableFuture<Boolean> upload(EventBatch batch) {
    uploadCount++;
    uploadCalls.add(batch);
      ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);
      Future<Boolean> future = executor.submit(() -> {
        System.out.println("Started " + batch.id() + " with " + batch.events().size() + " events");
        Thread.sleep(100);
        return true;
      });
    // Convert to CompletableFuture
    return CompletableFuture.supplyAsync(() -> {
      try {
        return future.get();
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }, executor);
  }

  @Override
  public void close() throws IOException {
    // no-op
  }

  public void reset() {
    uploadCount = 0;
    uploadCalls.clear();
  }
}
