package com.spotify.confidence;

import java.util.Map;

public interface Contextual {
  ConfidenceValue.Struct getContext();

  void setContext(ConfidenceValue.Struct context);

  default void setContext(Map<String, ConfidenceValue> context) {
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
