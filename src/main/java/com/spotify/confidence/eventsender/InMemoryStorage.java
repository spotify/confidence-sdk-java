package com.spotify.confidence.eventsender;

import java.util.ArrayList;
import java.util.List;

public class InMemoryStorage implements EventSenderStorage {
  private final List<Event> events = new ArrayList<>();
  private final List<EventBatch> batches = new ArrayList<>();

  @Override
  public synchronized void write(Event event) {
    this.events.add(event);
  }

  public synchronized List<EventBatch> createBatch() {
    if (!events.isEmpty()) {
      final EventBatch batch = new EventBatch(List.copyOf(events));
      events.clear();
      batches.add(batch);
    }
    return batches;
  }

  @Override
  public synchronized void deleteBatch(String batchId) {
    batches.removeIf(eventBatch -> eventBatch.id().equals(batchId));
  }
}