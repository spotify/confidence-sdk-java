package com.spotify.confidence;

import java.util.Map;

public interface EventSender extends Contextual {
  public void send(String name, ConfidenceValue.Struct message);

  public default void send(String name) {
    send(name, ConfidenceValue.Struct.EMPTY);
  }

  @Override
  EventSender withContext(ConfidenceValue.Struct context);

  @Override
  default EventSender withContext(Map<String, ConfidenceValue> context) {
    return withContext(ConfidenceValue.Struct.of(context));
  }
}
