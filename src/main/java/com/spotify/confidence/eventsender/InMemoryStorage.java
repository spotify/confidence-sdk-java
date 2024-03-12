package com.spotify.confidence.eventsender;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;

public class InMemoryStorage implements EventSenderStorage {
  private final List<Event> events = new ArrayList<>();
  private final List<EventBatch> readyBatches = new ArrayList<>();
  final Semaphore semaphore = new Semaphore(1);

  @Override
  public void write(Event event) {
    runWithSemaphore(
        () -> {
          this.events.add(event);
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

  @Override
  public void batch() {
    runWithSemaphore(
        () -> {
          EventBatch batch = new EventBatch(List.copyOf(events));
          events.clear();
          readyBatches.add(batch);
        });
  }

  @Override
  public List<EventBatch> readyEvents() {
    return readyBatches;
  }

  @Override
  public void deleteBatch(String batchId) {
    runWithSemaphore(
        () -> {
          readyBatches.removeIf(eventBatch -> eventBatch.id().equals(batchId));
          System.out.println("deleted");
        });
  }
}
