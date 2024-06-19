package com.spotify.confidence;

import com.google.common.annotations.Beta;
import java.io.Closeable;
import java.util.Map;

@Beta
public interface EventSender extends Contextual, Closeable {
  public void track(String eventName, ConfidenceValue.Struct data);

  public void track(String eventName);

  void flush();

  @Override
  EventSender withContext(ConfidenceValue.Struct context);

  @Override
  default EventSender withContext(Map<String, ConfidenceValue> context) {
    return withContext(ConfidenceValue.Struct.of(context));
  }
}
