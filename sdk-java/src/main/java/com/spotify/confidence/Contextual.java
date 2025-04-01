package com.spotify.confidence;

import com.google.common.annotations.Beta;
import java.util.Map;

/**
 * Interface for managing contextual data in Confidence SDK. Provides methods to set, update, and
 * clear context data used for feature flag evaluation and event tracking.
 */
@Beta
public interface Contextual {
  /**
   * Gets the current context data.
   *
   * @return The current context as a {@link ConfidenceValue.Struct}
   */
  ConfidenceValue.Struct getContext();

  /**
   * Sets the context data using a ConfidenceValue.Struct.
   *
   * @param context The new context data to set
   */
  void setContext(ConfidenceValue.Struct context);

  /**
   * Sets the context data using a Map of key-value pairs.
   *
   * @param context Map of context key-value pairs
   */
  default void setContext(Map<String, ConfidenceValue> context) {
    setContext(ConfidenceValue.Struct.of(context));
  }

  /**
   * Updates a single entry in the context data.
   *
   * @param key The key to update
   * @param value The new value for the key
   */
  void updateContextEntry(String key, ConfidenceValue value);

  /**
   * Removes a single entry from the context data.
   *
   * @param key The key to remove
   */
  void removeContextEntry(String key);

  /** Clears all context data. */
  void clearContext();

  /**
   * Creates a new instance with the specified context.
   *
   * @param context The new context to set
   * @return A new instance with the specified context
   */
  Contextual withContext(ConfidenceValue.Struct context);

  /**
   * Creates a new instance with the specified context map.
   *
   * @param context Map of context key-value pairs
   * @return A new instance with the specified context
   */
  default Contextual withContext(Map<String, ConfidenceValue> context) {
    return withContext(ConfidenceValue.Struct.of(context));
  }
}
