package com.spotify.confidence;

import com.google.common.annotations.Beta;
import java.util.Map;

@Beta
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
