package com.spotify.confidence;

import java.util.function.Supplier;

/** A retry strategy that doesn't retry - executes the operation once and returns or throws. */
class NoRetryStrategy implements RetryStrategy {
  @Override
  public <T> T execute(Supplier<T> operation, String operationName) {
    return operation.get();
  }
}
