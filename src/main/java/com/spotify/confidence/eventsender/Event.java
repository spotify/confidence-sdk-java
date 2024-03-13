package com.spotify.confidence.eventsender;

public class Event {
  private final String name;
  private final Value.Struct message;
  private final Value.Struct context;
  private final long emitTimeSeconds;

  public Event(String name, Value.Struct message, Value.Struct context) {
    this.name = name;
    this.message = message;
    this.context = context;
    this.emitTimeSeconds = System.currentTimeMillis() / 1000;
  }

  public String name() {
    return name;
  }

  public long emitTime() {
    return emitTimeSeconds;
  }

  public Value.Struct message() {
    return message;
  }

  public Value.Struct context() {
    return context;
  }
}
