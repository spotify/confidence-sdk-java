package com.spotify.confidence;

import java.util.Map;

/**
 * Interface for evaluating feature flags with context. Extends {@link Contextual} to provide
 * context-aware flag evaluation capabilities.
 */
public interface FlagEvaluator extends Contextual {
  /**
   * Gets the value of a feature flag for the current context.
   *
   * @param key The key identifying the feature flag
   * @param defaultValue The default value to return if the flag is not found or evaluation fails
   * @param <T> The type of the flag value
   * @return The evaluated flag value or the default value if evaluation fails
   */
  <T> T getValue(String key, T defaultValue);

  /**
   * Gets a detailed evaluation of a feature flag for the current context.
   *
   * @param key The key identifying the feature flag
   * @param defaultValue The default value to return if the flag is not found or evaluation fails
   * @param <T> The type of the flag value
   * @return A {@link FlagEvaluation} containing the evaluated value and evaluation details
   */
  <T> FlagEvaluation<T> getEvaluation(String key, T defaultValue);

  /**
   * Creates a new instance with the specified context.
   *
   * @param context The new context to set
   * @return A new instance with the specified context
   */
  @Override
  FlagEvaluator withContext(ConfidenceValue.Struct context);

  /**
   * Creates a new instance with the specified context map.
   *
   * @param context Map of context key-value pairs
   * @return A new instance with the specified context
   */
  @Override
  default FlagEvaluator withContext(Map<String, ConfidenceValue> context) {
    return withContext(ConfidenceValue.Struct.of(context));
  }
}
