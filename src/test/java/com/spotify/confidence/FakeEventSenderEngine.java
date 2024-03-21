package com.spotify.confidence;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class FakeEventSenderEngine implements EventSenderEngine {

  List<Event> events = new ArrayList<>();
  boolean closed;

  @Override
  public void close() throws IOException {
    this.closed = true;
  }

  @Override
  public void send(
      String name, ConfidenceValue.Struct context, Optional<ConfidenceValue.Struct> message) {
    events.add(new Event(name, message.orElse(ConfidenceValue.Struct.EMPTY), context));
  }
}
