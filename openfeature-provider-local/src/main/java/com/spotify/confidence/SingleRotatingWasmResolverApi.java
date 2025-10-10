package com.spotify.confidence;

import com.spotify.confidence.flags.resolver.v1.ResolveWithStickyRequest;
import com.spotify.confidence.shaded.flags.resolver.v1.ResolveFlagsResponse;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

class SingleRotatingWasmResolverApi implements RotatingWasmResolverApi {
  private final AtomicReference<LockedWasmResolverApi> lockedResolverApi = new AtomicReference<>();
  private final StickyResolveStrategy stickyResolveStrategy;
  private final WasmFlagLogger flagLogger;

  public SingleRotatingWasmResolverApi(
      WasmFlagLogger flagLogger,
      byte[] initialState,
      String accountId,
      StickyResolveStrategy stickyResolveStrategy) {
    this.stickyResolveStrategy = stickyResolveStrategy;
    this.flagLogger = flagLogger;

    // Create initial instance
    final WasmResolveApi initialInstance = new WasmResolveApi(flagLogger);
    initialInstance.setResolverState(initialState, accountId);
    final var stickyResolverApi = new StickyResolverApi(initialInstance, stickyResolveStrategy);
    this.lockedResolverApi.set(new LockedWasmResolverApi(stickyResolverApi));
  }

  @Override
  public void rotate(byte[] state, String accountId) {
    // Create new instance with updated state
    final WasmResolveApi newInstance = new WasmResolveApi(flagLogger);
    newInstance.setResolverState(state, accountId);

    final var stickyResolverApi = new StickyResolverApi(newInstance, stickyResolveStrategy);
    final LockedWasmResolverApi oldInstance =
        lockedResolverApi.getAndSet(new LockedWasmResolverApi(stickyResolverApi));
    if (oldInstance != null) {
      oldInstance.flush();
    }
  }

  @Override
  public void close() {}

  @Override
  public CompletableFuture<ResolveFlagsResponse> resolve(ResolveWithStickyRequest request) {
    try {
      return lockedResolverApi.get().resolve(request);
    } catch (IsClosedException e) {
      return resolve(request);
    }
  }
}
