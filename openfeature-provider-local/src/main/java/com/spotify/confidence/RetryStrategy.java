package com.spotify.confidence;

import java.util.function.Supplier;

/** Strategy for retrying operations that fail with transient errors. */
interface RetryStrategy {
  /**
   * Executes an operation with the configured retry strategy.
   *
   * @param operation The operation to execute
   * @param operationName Name of the operation for error messages
   * @param <T> Return type of the operation
   * @return The result of the operation
   * @throws RuntimeException if the operation fails
   */
  <T> T execute(Supplier<T> operation, String operationName);
}
