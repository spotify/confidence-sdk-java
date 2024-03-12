package com.spotify.confidence.eventsender;

public class Event {
  private final String name;
  private final ConfidenceValue.Struct message;
  private final ConfidenceValue.Struct context;

  public Event(String name, ConfidenceValue.Struct message, ConfidenceValue.Struct context) {
    this.name = name;
    this.message = message;
    this.context = context;
  }
}
