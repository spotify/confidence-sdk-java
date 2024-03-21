package com.spotify.confidence;

import java.util.ArrayList;
import java.util.List;

class InMemoryStorage implements EventSenderStorage {
  private final List<Event> events = new ArrayList<>();
  private final List<EventBatch> batches = new ArrayList<>();

  @Override
  public synchronized void write(Event event) {
    System.out.println(">> [Memory] New Events: " + events.size() + " - In Batches: " + batches.stream().mapToInt(b -> b.events().size()).sum());
    this.events.add(event);
  }

  public synchronized void createBatch() {
    if (!events.isEmpty()) {
      final EventBatch batch = new EventBatch(List.copyOf(events));
      events.clear();
      batches.add(batch);
      System.out.println(">> [Memory] New Events: " + events.size() + " - In Batches: " + batches.stream().mapToInt(b -> b.events().size()).sum());
    }
  }

  public List<EventBatch> getBatches() {
    return batches;
  }

  @Override
  public synchronized void deleteBatch(String batchId) {
    batches.removeIf(eventBatch -> eventBatch.id().equals(batchId));
    System.out.println(">> [Memory] New Events: " + events.size() + " - In Batches: " + batches.stream().mapToInt(b -> b.events().size()).sum());
  }
}
