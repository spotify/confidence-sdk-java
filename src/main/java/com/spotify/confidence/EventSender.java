package com.spotify.confidence;

import com.google.common.collect.ImmutableMap;

public interface EventSender extends Contextual {
  public void send(String name, ConfidenceValue.Struct message);

  public default void send(String name) {
    send(name, ConfidenceValue.Struct.EMPTY);
  }

  @Override
  EventSender withContext(ConfidenceValue.Struct entries);

  @Override
  default EventSender withContext(ImmutableMap<String, ConfidenceValue> entries) {
    return withContext(ConfidenceValue.Struct.of(entries));
  }
}
