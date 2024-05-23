package com.spotify.confidence.common;

import com.google.common.annotations.Beta;
import java.util.Map;

@Beta
public interface EventSender extends Contextual {
  public void track(String eventName, ConfidenceValue.Struct message);

  public void track(String eventName);

  @Override
  public EventSender withContext(ConfidenceValue.Struct context);

  @Override
  public default EventSender withContext(Map<String, ConfidenceValue> context) {
    return withContext(ConfidenceValue.Struct.of(context));
  }
}
