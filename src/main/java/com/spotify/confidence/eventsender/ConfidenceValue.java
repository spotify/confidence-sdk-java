package com.spotify.confidence.eventsender;

import com.google.common.collect.ImmutableMap;
import java.util.Map;

// Immutable value hierarchy
abstract class ConfidenceValue {
  static ConfidenceValue.Struct of(ImmutableMap<String, ConfidenceValue> map) {
    return new ConfidenceValue.Struct(map);
  }

  static ConfidenceValue.String of(String value) {
    return new ConfidenceValue.String(value);
  }

  static class String extends ConfidenceValue {
    private final String value;

    public String(String value) {
      this.value = value;
    }
  }

  static class Struct extends ConfidenceValue {
    private final Map<String, ConfidenceValue> value;

    public Struct(ImmutableMap<String, ConfidenceValue> map) {
      this.value = map;
    }
  }
}
