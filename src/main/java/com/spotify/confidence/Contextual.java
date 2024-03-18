package com.spotify.confidence;

import com.google.common.collect.ImmutableMap;

public interface Contextual {
  ConfidenceValue.Struct getContext();

  void setContext(ConfidenceValue.Struct context);

  default void setContext(ImmutableMap<String, ConfidenceValue> context) {
    setContext(ConfidenceValue.Struct.of(context));
  }
  ;

  void updateContext(String key, ConfidenceValue value);

  void removeContext(String key);

  void clearContext();

  Contextual withContext(ConfidenceValue.Struct entries);

  default Contextual withContext(ImmutableMap<String, ConfidenceValue> entries) {
    return withContext(ConfidenceValue.Struct.of(entries));
  }
}
