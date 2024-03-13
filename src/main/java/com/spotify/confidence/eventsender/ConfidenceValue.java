package com.spotify.confidence.eventsender;

import com.google.common.collect.ImmutableMap;
import java.util.Map;

// Immutable value hierarchy
abstract class ConfidenceValue {
  static ConfidenceValue.Struct of(ImmutableMap<java.lang.String, ConfidenceValue> map) {
    return new ConfidenceValue.Struct(map);
  }

  static ConfidenceValue.String of(java.lang.String value) {
    return new ConfidenceValue.String(value);
  }

  public static ConfidenceValue.Struct emptyStruct() {
    return new ConfidenceValue.Struct(ImmutableMap.of());
  }

  static class String extends ConfidenceValue {
    private final java.lang.String value;

    private String(java.lang.String value) {
      this.value = value;
    }

    public java.lang.String value() {
      return value;
    }
  }

  static class Struct extends ConfidenceValue {
    private final Map<java.lang.String, ConfidenceValue> value;

    private Struct(ImmutableMap<java.lang.String, ConfidenceValue> map) {
      this.value = map;
    }

    public Map<java.lang.String, ConfidenceValue> value() {
      return value;
    }
  }
}
