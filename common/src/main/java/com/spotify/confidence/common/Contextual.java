package com.spotify.confidence.common;

import com.google.common.annotations.Beta;
import java.util.Map;

@Beta
public interface Contextual {
  public ConfidenceValue.Struct getContext();

  public void setContext(ConfidenceValue.Struct context);

  public default void setContext(Map<String, ConfidenceValue> context) {
    setContext(ConfidenceValue.Struct.of(context));
  }

  void updateContextEntry(String key, ConfidenceValue value);

  void removeContextEntry(String key);

  void clearContext();

  Contextual withContext(ConfidenceValue.Struct context);

  default Contextual withContext(Map<String, ConfidenceValue> context) {
    return withContext(ConfidenceValue.Struct.of(context));
  }
}
