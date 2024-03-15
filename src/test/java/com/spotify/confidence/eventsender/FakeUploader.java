package com.spotify.confidence.eventsender;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

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
    if (failAtUploadWithIndex.contains(uploadCount)) {
      return CompletableFuture.completedFuture(false);
    }
    return CompletableFuture.completedFuture(true);
  }

  @Override
  public void close() throws IOException {
    uploadCount = 0;
    uploadCalls.clear();
    // no-op
  }
}
