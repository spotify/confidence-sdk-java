package com.spotify.confidence;

class Event {
  private final String name;
  private final ConfidenceValue.Struct message;
  private final ConfidenceValue.Struct context;
  private final long emitTimeSeconds;

  public Event(
      String name,
      ConfidenceValue.Struct message,
      ConfidenceValue.Struct context,
      long emitTimeSeconds) {
    this.name = name;
    this.message = message;
    this.context = context;
    this.emitTimeSeconds = emitTimeSeconds;
  }

  public String name() {
    return name;
  }

  public long emitTime() {
    return emitTimeSeconds;
  }

  public ConfidenceValue.Struct message() {
    return message;
  }

  public ConfidenceValue.Struct context() {
    return context;
  }
}
