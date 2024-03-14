package com.spotify.confidence.eventsender;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.stream.Collectors;

public class InMemoryStorage implements EventSenderStorage {
  private final List<Event> events = new ArrayList<>();
  private final List<EventBatch> batches = new ArrayList<>();
  final Semaphore semaphore = new Semaphore(1);

  @Override
  public void write(Event event) {
    runWithSemaphore(() -> this.events.add(event));
  }

  public void createBatch() {
    runWithSemaphore(
        () -> {
          final EventBatch batch = new EventBatch(List.copyOf(events));
          events.clear();
          batches.add(batch);
          System.out.println(
              "Created batch "
                  + batch.id()
                  + " with "
                  + batch.events.stream().map(e -> e.name()).collect(Collectors.toList()));
        });
  }

  public List<EventBatch> getBatches() {
    return batches;
  }

  @Override
  public void deleteBatch(String batchId) {
    runWithSemaphore(() -> batches.removeIf(eventBatch -> eventBatch.id().equals(batchId)));
  }

  @Override
  public void deleteBatch(String batchId, List<Event> toBeRetried) {
    runWithSemaphore(
        () -> {
          batches.removeIf(eventBatch -> eventBatch.id().equals(batchId));
          events.addAll(toBeRetried);
          System.out.println(
              "Deleted batch "
                  + batchId
                  + ". Adding events: "
                  + toBeRetried.stream().map(e -> e.name()).collect(Collectors.toList()));
        });
  }

  void runWithSemaphore(Runnable codeBlock) {
    try {
      semaphore.acquire(); // Acquire the semaphore, blocking if necessary
      // Execute the provided code block
      codeBlock.run();
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    } finally {
      semaphore.release(); // Release the semaphore after executing the code block
    }
  }
}
