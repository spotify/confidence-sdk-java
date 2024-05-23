package com.spotify.confidence.common;

import static com.spotify.confidence.common.EventUploader.event;

import com.spotify.confidence.common.ConfidenceValue.Struct;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class FakeEventSenderEngine implements EventSenderEngine {

  final Clock clock;

  public FakeEventSenderEngine(Clock clock) {
    this.clock = clock;
  }

  List<com.spotify.confidence.events.v1.Event> events = new ArrayList<>();
  public boolean closed;

  @Override
  public void close() throws IOException {
    this.closed = true;
  }

  @Override
  public void emit(String name, Struct context, Optional<Struct> message) {
    events.add(event(name, context, message).setEventTime(clock.getTimestamp()).build());
  }
}
