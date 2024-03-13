package com.spotify.confidence.eventsender;

import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class IntermittentErrorUploader implements EventUploader {
  private final List<Integer> failAtUploadWithIndex;
  private int uploadCount = 0;
  public List<String> uploadCalls = new ArrayList<>();

  public IntermittentErrorUploader(List<Integer> failAtUploadWithIndex) {
    this.failAtUploadWithIndex = failAtUploadWithIndex;
  }

  @Override
  public CompletableFuture<List<Event>> upload(EventBatch batch) {
    uploadCount++;
    uploadCalls.add(batch.id());
    if (failAtUploadWithIndex.contains(uploadCount)) {
      return CompletableFuture.completedFuture(ImmutableList.copyOf(batch.events));
    }
    return CompletableFuture.completedFuture(ImmutableList.of());
  }

  @Override
  public void close() {
    uploadCount = 0;
    uploadCalls.clear();
    // no-op
  }
}
