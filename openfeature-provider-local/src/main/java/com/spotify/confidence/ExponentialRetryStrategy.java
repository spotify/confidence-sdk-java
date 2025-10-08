package com.spotify.confidence;

import com.dylibso.chicory.wasm.ChicoryException;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A retry strategy that retries operations with exponential backoff for Chicory WASM runtime
 * exceptions.
 */
class ExponentialRetryStrategy implements RetryStrategy {
  private static final Logger logger = LoggerFactory.getLogger(ExponentialRetryStrategy.class);

  private final int maxRetries;
  private final long initialBackoffMs;
  private final double backoffMultiplier;

  /**
   * Creates an exponential retry strategy with the specified configuration.
   *
   * @param maxRetries Maximum number of retries (not including the initial attempt)
   * @param initialBackoffMs Initial backoff delay in milliseconds
   * @param backoffMultiplier Multiplier for exponential backoff
   */
  public ExponentialRetryStrategy(int maxRetries, long initialBackoffMs, double backoffMultiplier) {
    this.maxRetries = maxRetries;
    this.initialBackoffMs = initialBackoffMs;
    this.backoffMultiplier = backoffMultiplier;
  }

  /**
   * Creates an exponential retry strategy with default configuration (3 retries, 100ms initial).
   */
  public ExponentialRetryStrategy() {
    this(3, 100, 2.0);
  }

  @Override
  public <T> T execute(Supplier<T> operation, String operationName) {
    RuntimeException lastException = null;
    long backoffMs = initialBackoffMs;

    for (int attempt = 0; attempt <= maxRetries; attempt++) {
      try {
        return operation.get();
      } catch (ChicoryException e) {
        logger.warn("{} attempt {} failed: {}", operationName, attempt + 1, e.getMessage());
        lastException = e;
        if (attempt < maxRetries) {
          try {
            Thread.sleep(backoffMs);
          } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(operationName + " interrupted during retry backoff", ie);
          }
          backoffMs = (long) (backoffMs * backoffMultiplier);
        }
      }
    }

    throw new RuntimeException(
        operationName + " failed after " + (maxRetries + 1) + " attempts", lastException);
  }
}
