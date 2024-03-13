package com.spotify.confidence.eventsender;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class IntermittentErrorUploader implements EventUploader {
  private final List<Integer> failAtUploadWithIndex;
  private int uploadCount = 0;
  public List<String> uploadCalls = new ArrayList<>();

  public IntermittentErrorUploader(List<Integer> failAtUploadWithIndex) {
    this.failAtUploadWithIndex = failAtUploadWithIndex;
  }

  @Override
  public CompletableFuture<Boolean> upload(EventBatch batch) {
    System.out.println(
        "Uploading batch: "
            + batch.events().stream().map(Event::name).collect(Collectors.toList()));
    uploadCount++;
    uploadCalls.add(batch.id());
    if (failAtUploadWithIndex.contains(uploadCount)) {
      return CompletableFuture.completedFuture(false);
    }
    return CompletableFuture.completedFuture(true);
  }

  @Override
  public void close() {
    uploadCount = 0;
    uploadCalls.clear();
    // no-op
  }
}
