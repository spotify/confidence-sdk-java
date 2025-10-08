package com.spotify.confidence;

import com.spotify.confidence.flags.resolver.v1.ResolveWithStickyRequest;
import com.spotify.confidence.shaded.flags.resolver.v1.ResolveFlagsRequest;
import com.spotify.confidence.shaded.flags.resolver.v1.ResolveFlagsResponse;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-local wrapper for SwapWasmResolverApi that provides each thread with its own instance.
 * This eliminates contention on the locks inside SwapWasmResolverApi when called from multiple
 * threads.
 */
class ThreadLocalSwapWasmResolverApi implements ResolverApi {
  private final WasmFlagLogger flagLogger;
  private final StickyResolveStrategy stickyResolveStrategy;
  private final RetryStrategy retryStrategy;
  private volatile byte[] currentState;
  private volatile String currentAccountId;

  // Track all thread-local instances for state updates and cleanup
  private final Map<Thread, SwapWasmResolverApi> instances = new ConcurrentHashMap<>();

  private final ThreadLocal<SwapWasmResolverApi> threadLocalResolver;

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

    this.threadLocalResolver =
        ThreadLocal.withInitial(
            () -> {
              final var instance =
                  new SwapWasmResolverApi(
                      this.flagLogger,
                      this.currentState,
                      this.currentAccountId,
                      this.stickyResolveStrategy,
                      this.retryStrategy);
              instances.put(Thread.currentThread(), instance);
              return instance;
            });
  }

  /**
   * Updates state and flushes logs for all thread-local resolver instances. This method is
   * typically called by a scheduled task to refresh the resolver state.
   */
  @Override
  public void updateStateAndFlushLogs(byte[] state, String accountId) {
    this.currentState = state;
    this.currentAccountId = accountId;

    // Update all existing thread-local instances
    instances.values().forEach(resolver -> resolver.updateStateAndFlushLogs(state, accountId));

    // Clean up instances for threads that no longer exist
    instances
        .entrySet()
        .removeIf(
            entry -> {
              if (!entry.getKey().isAlive()) {
                entry.getValue().close();
                return true;
              }
              return false;
            });
  }

  /** Delegates resolveWithSticky to the thread-local SwapWasmResolverApi instance. */
  @Override
  public CompletableFuture<ResolveFlagsResponse> resolveWithSticky(
      ResolveWithStickyRequest request) {
    return threadLocalResolver.get().resolveWithSticky(request);
  }

  /** Delegates resolve to the thread-local SwapWasmResolverApi instance. */
  @Override
  public ResolveFlagsResponse resolve(ResolveFlagsRequest request) {
    return threadLocalResolver.get().resolve(request);
  }

  /** Closes all thread-local resolver instances and clears the tracking map. */
  @Override
  public void close() {
    instances.values().forEach(SwapWasmResolverApi::close);
    instances.clear();
    threadLocalResolver.remove();
  }
}
