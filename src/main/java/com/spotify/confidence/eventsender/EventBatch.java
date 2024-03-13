package com.spotify.confidence.eventsender;

import java.util.List;
import java.util.UUID;

public class EventBatch {
  public final List<Event> events;
  private final String id = UUID.randomUUID().toString();

  public EventBatch(List<Event> events) {
    this.events = events;
  }

  public String id() {
    return id;
  }

  public List<Event> events() {
    return events;
  }
}
