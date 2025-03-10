package com.spotify.confidence;

import com.spotify.confidence.shaded.flags.resolver.v1.ResolveFlagsResponse;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConfidenceStub extends Confidence {

  private final Map<String, Object> valueMap = new HashMap<>();
  private final Map<String, FlagEvaluationConfig> evaluationConfigMap = new HashMap<>();
  private static final Logger log = LoggerFactory.getLogger(ConfidenceStub.class);

  private ConfidenceStub() {
    // Private constructor to prevent direct instantiation
  }

  public static ConfidenceStub createStub() {
    return new ConfidenceStub();
  }

  @Override
  protected ClientDelegate client() {
    // Return a mock or no-op client delegate
    return new MockClientDelegate();
  }

  @Override
  public <T> T getValue(String key, T defaultValue) {
    // Check if a configured value exists
    if (valueMap.containsKey(key)) {
      Object value = valueMap.get(key);
      if (defaultValue != null && defaultValue.getClass().isInstance(value)) {
        return (T) value;
      } else {
        // Log a warning or throw an exception if the type doesn't match
        log.warn("Type mismatch for key: " + key);
      }
    }
    // Return the default value if not configured or type mismatch
    return defaultValue;
  }

  @Override
  public <T> FlagEvaluation<T> getEvaluation(String key, T defaultValue) {
    // Use getValue to retrieve the configured value or default
    T value = getValue(key, defaultValue);
    // Retrieve additional configuration for FlagEvaluation
    FlagEvaluationConfig config =
        evaluationConfigMap.getOrDefault(key, new FlagEvaluationConfig("stub", "MOCK", null, null));
    // Return a FlagEvaluation with the retrieved value and additional fields
    return new FlagEvaluation<>(
        value, config.variant, config.reason, config.errorType, config.errorMessage);
  }

  @Override
  public CompletableFuture<ResolveFlagsResponse> resolveFlags(String flagName) {
    // Return a completed future with a mock response
    return CompletableFuture.completedFuture(ResolveFlagsResponse.getDefaultInstance());
  }

  @Override
  public void track(String eventName) {
    // No-op for tracking
  }

  @Override
  public void track(String eventName, ConfidenceValue.Struct data) {
    // No-op for tracking with data
  }

  @Override
  public void close() {
    // No-op for close
  }

  @Override
  public void flush() {
    // No-op for flush
  }

  // Method to configure return values
  public <T> void configureValue(String key, T value) {
    valueMap.put(key, value);
  }

  // Method to configure FlagEvaluation fields
  public void configureEvaluationFields(
      String key, String variant, String reason, ErrorType errorType, String errorMessage) {
    evaluationConfigMap.put(
        key, new FlagEvaluationConfig(variant, reason, errorType, errorMessage));
  }

  // Mock implementation of ClientDelegate
  private static class MockClientDelegate extends ClientDelegate {
    private MockClientDelegate() {
      super(null, null, null, "");
    }

    @Override
    public void emit(
        String name, ConfidenceValue.Struct context, Optional<ConfidenceValue.Struct> message) {
      // No-op
    }

    @Override
    public void flush() {
      // No-op
    }

    @Override
    public CompletableFuture<ResolveFlagsResponse> resolveFlags(
        String flag, ConfidenceValue.Struct context) {
      return CompletableFuture.completedFuture(ResolveFlagsResponse.getDefaultInstance());
    }

    @Override
    public void close() {
      // No-op
    }
  }

  // Inner class to hold FlagEvaluation configuration
  private static class FlagEvaluationConfig {
    String variant;
    String reason;
    ErrorType errorType;
    String errorMessage;

    FlagEvaluationConfig(String variant, String reason, ErrorType errorType, String errorMessage) {
      this.variant = variant;
      this.reason = reason;
      this.errorType = errorType;
      this.errorMessage = errorMessage;
    }
  }

  // Additional methods to configure the stub can be added here
}
