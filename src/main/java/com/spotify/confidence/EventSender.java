package com.spotify.confidence;

import com.google.common.annotations.Beta;
import java.util.Map;

@Beta
public interface EventSender extends Contextual {
  public void send(String name, ConfidenceValue.Struct message);

  public void send(String name);

  @Override
  EventSender withContext(ConfidenceValue.Struct context);

  @Override
  default EventSender withContext(Map<String, ConfidenceValue> context) {
    return withContext(ConfidenceValue.Struct.of(context));
  }
}
