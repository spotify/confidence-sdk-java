package com.spotify.confidence.eventsender;

import com.google.common.collect.ImmutableMap;
import java.util.Map;

// Immutable value hierarchy
public abstract class ConfidenceValue {
  static ConfidenceValue.Struct of(ImmutableMap<String, ConfidenceValue> map) {
    return new ConfidenceValue.Struct(map);
  }

  static ConfidenceValue.String of(java.lang.String value) {
    return new ConfidenceValue.String(value);
  }

  public static class String extends ConfidenceValue {
    private final java.lang.String value;

    public String(java.lang.String value) {
      this.value = value;
    }

    public java.lang.String toString() {
      return value;
    }
  }

  public static class Struct extends ConfidenceValue {
    private final Map<String, ConfidenceValue> value;

    public Struct(ImmutableMap<String, ConfidenceValue> map) {
      this.value = map;
    }

    public Map<String, ConfidenceValue> toMap() {
      return value;
    }
  }
}
