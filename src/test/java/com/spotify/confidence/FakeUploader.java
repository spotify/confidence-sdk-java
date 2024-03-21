package com.spotify.confidence;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class FakeUploader implements EventUploader {
  private final List<Integer> failAtUploadWithIndex;
  private int uploadCount = 0;
  private int sentEvents = 0;
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
    uploadCount = uploadCount + batch.events().size();
    System.out.println(uploadCount);
    return CompletableFuture.completedFuture(true);
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
