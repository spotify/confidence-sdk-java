package com.spotify.confidence;

import com.spotify.confidence.flags.resolver.v1.ResolveWithStickyRequest;
import com.spotify.confidence.shaded.flags.resolver.v1.ResolveFlagsRequest;
import com.spotify.confidence.shaded.flags.resolver.v1.ResolveFlagsResponse;
import com.spotify.futures.CompletableFutures;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
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
  private volatile byte[] currentState;
  private volatile String currentAccountId;

  // Pre-initialized resolver instances mapped by core index
  private final Map<Integer, SwapWasmResolverApi> resolverInstances = new ConcurrentHashMap<>();
  private final int numInstances;
  private final AtomicInteger nextInstanceIndex = new AtomicInteger(0);
  private final ThreadLocal<Integer> threadInstanceIndex =
      new ThreadLocal<>() {
        @Override
        protected Integer initialValue() {
          return nextInstanceIndex.getAndIncrement() % numInstances;
        }
      };

  public ThreadLocalSwapWasmResolverApi(
      WasmFlagLogger flagLogger,
      byte[] initialState,
      String accountId,
      StickyResolveStrategy stickyResolveStrategy) {
    this.flagLogger = flagLogger;
    this.stickyResolveStrategy = stickyResolveStrategy;
    this.currentState = initialState;
    this.currentAccountId = accountId;

    // Pre-create instances based on CPU core count for optimal performance
    this.numInstances = Runtime.getRuntime().availableProcessors();
    logger.info(
        "Initialized ThreadLocalSwapWasmResolverApi with {} available processors", numInstances);
    final var futures = new ArrayList<CompletableFuture<Void>>(numInstances);

    IntStream.range(0, numInstances)
        .forEach(
            i ->
                futures.add(
                    CompletableFuture.runAsync(
                        () -> {
                          final var instance =
                              new SwapWasmResolverApi(
                                  this.flagLogger,
                                  this.currentState,
                                  this.currentAccountId,
                                  this.stickyResolveStrategy);
                          resolverInstances.put(i, instance);
                        })));
    CompletableFutures.allAsList(futures).join();
  }

  /**
   * Updates state and flushes logs for all pre-initialized resolver instances. This method is
   * typically called by a scheduled task to refresh the resolver state.
   */
  @Override
  public void updateStateAndFlushLogs(byte[] state, String accountId) {
    this.currentState = state;
    this.currentAccountId = accountId;

    final var futures =
        resolverInstances.values().stream()
            .map(v -> CompletableFuture.runAsync(() -> v.updateStateAndFlushLogs(state, accountId)))
            .toList();
    CompletableFutures.allAsList(futures).join();
  }

  /**
   * Maps the current thread to a resolver instance using round-robin assignment. Each thread gets
   * assigned to an instance index when first accessed, ensuring even distribution across available
   * instances.
   */
  private SwapWasmResolverApi getResolverForCurrentThread() {
    final int instanceIndex = threadInstanceIndex.get();
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
