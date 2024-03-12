package com.spotify.confidence.eventsender;

public class Event {
  final String name;
  final ConfidenceValue.Struct message;
  final ConfidenceValue.Struct context;

  public Event(String name, ConfidenceValue.Struct message, ConfidenceValue.Struct context) {
    this.name = name;
    this.message = message;
    this.context = context;
  }
}
