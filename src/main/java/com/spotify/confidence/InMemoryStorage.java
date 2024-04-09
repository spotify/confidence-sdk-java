package com.spotify.confidence;

import java.util.ArrayList;
import java.util.List;

class InMemoryStorage implements EventSenderStorage {
  private List<Event> events = new ArrayList<>();
  private final List<EventBatch> batches = new ArrayList<>();

  @Override
  public synchronized int write(Event event) {
    this.events.add(event);
    return this.events.size();
  }

  public synchronized void createBatch() {
    if (!events.isEmpty()) {
      final EventBatch batch = new EventBatch(events);
      batches.add(batch);
      events = new ArrayList<>();
    }
  }

  public synchronized List<EventBatch> getBatches() {
    return List.copyOf(batches);
  }

  @Override
  public synchronized void deleteBatch(String batchId) {
    batches.removeIf(eventBatch -> eventBatch.id().equals(batchId));
  }
}
