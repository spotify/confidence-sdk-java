package com.spotify.confidence;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class FakeEventSenderEngine implements EventSenderEngine {

  List<Event> events = new ArrayList<>();
  boolean closed;

  @Override
  public void close() throws IOException {
    this.closed = true;
  }

  @Override
  public void send(String name, ConfidenceValue.Struct context) {
    send(name, ConfidenceValue.Struct.EMPTY, context);
  }

  @Override
  public void send(String name, ConfidenceValue.Struct message, ConfidenceValue.Struct context) {
    events.add(new Event(name, message, context));
  }
}
