package com.spotify.confidence;

import com.spotify.confidence.flags.resolver.v1.ResolveWithStickyRequest;
import com.spotify.confidence.shaded.flags.resolver.v1.ResolveFlagsRequest;
import com.spotify.confidence.shaded.flags.resolver.v1.ResolveFlagsResponse;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.IntStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Pre-initialized resolver instances mapped by thread ID to CPU core count. This eliminates both
 * lock contention and lazy initialization overhead.
 */
class ThreadLocalSwapWasmResolverApi implements ResolverApi {
  private static final Logger logger =
      LoggerFactory.getLogger(ThreadLocalSwapWasmResolverApi.class);
  private final WasmFlagLogger flagLogger;
  private final StickyResolveStrategy stickyResolveStrategy;
  private final RetryStrategy retryStrategy;
  private volatile byte[] currentState;
  private volatile String currentAccountId;

  // Pre-initialized resolver instances mapped by core index
  private final Map<Integer, SwapWasmResolverApi> resolverInstances = new ConcurrentHashMap<>();
  private final int numInstances;

  public ThreadLocalSwapWasmResolverApi(
      WasmFlagLogger flagLogger,
      byte[] initialState,
      String accountId,
      StickyResolveStrategy stickyResolveStrategy,
      RetryStrategy retryStrategy) {
    this.flagLogger = flagLogger;
    this.stickyResolveStrategy = stickyResolveStrategy;
    this.retryStrategy = retryStrategy;
    this.currentState = initialState;
    this.currentAccountId = accountId;

    // Pre-create instances based on CPU core count for optimal performance
    this.numInstances = Runtime.getRuntime().availableProcessors();
    logger.info(
        "Initialized ThreadLocalSwapWasmResolverApi with {} available processors", numInstances);
    IntStream.range(0, numInstances)
        .forEach(
            i -> {
              final var instance =
                  new SwapWasmResolverApi(
                      this.flagLogger,
                      this.currentState,
                      this.currentAccountId,
                      this.stickyResolveStrategy,
                      this.retryStrategy);
              resolverInstances.put(i, instance);
            });
  }

  /**
   * Updates state and flushes logs for all pre-initialized resolver instances. This method is
   * typically called by a scheduled task to refresh the resolver state.
   */
  @Override
  public void updateStateAndFlushLogs(byte[] state, String accountId) {
    this.currentState = state;
    this.currentAccountId = accountId;

    // Update all pre-initialized resolver instances
    resolverInstances
        .values()
        .forEach(resolver -> resolver.updateStateAndFlushLogs(state, accountId));
  }

  /**
   * Maps the current thread to a resolver instance based on thread ID. Uses modulo operation to
   * distribute threads across available instances.
   */
  private SwapWasmResolverApi getResolverForCurrentThread() {
    final int threadId = (int) Thread.currentThread().getId();
    final int instanceIndex = threadId % numInstances;
    return resolverInstances.get(instanceIndex);
  }

  /** Delegates resolveWithSticky to the assigned SwapWasmResolverApi instance. */
  @Override
  public CompletableFuture<ResolveFlagsResponse> resolveWithSticky(
      ResolveWithStickyRequest request) {
    return getResolverForCurrentThread().resolveWithSticky(request);
  }

  /** Delegates resolve to the assigned SwapWasmResolverApi instance. */
  @Override
  public ResolveFlagsResponse resolve(ResolveFlagsRequest request) {
    return getResolverForCurrentThread().resolve(request);
  }

  /**
   * Returns the number of pre-initialized resolver instances. This is primarily for debugging and
   * monitoring purposes.
   */
  public int getInstanceCount() {
    return resolverInstances.size();
  }

  /** Closes all pre-initialized resolver instances and clears the map. */
  @Override
  public void close() {
    resolverInstances.values().forEach(SwapWasmResolverApi::close);
    resolverInstances.clear();
  }
}
