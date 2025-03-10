package com.spotify.confidence;

import com.spotify.confidence.shaded.flags.resolver.v1.ResolveFlagsResponse;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class ConfidenceStub extends Confidence {

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
    // Return a default or mock value
    return defaultValue;
  }

  @Override
  public <T> FlagEvaluation<T> getEvaluation(String key, T defaultValue) {
    // Return a mock FlagEvaluation
    return new FlagEvaluation<>(defaultValue, "", "MOCK");
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

  // Additional methods to configure the stub can be added here
}
