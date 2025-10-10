package com.spotify.confidence;

import com.spotify.confidence.flags.resolver.v1.ResolveWithStickyRequest;
import com.spotify.confidence.shaded.flags.resolver.v1.ResolveFlagsResponse;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

class IsClosedException extends Exception {}

class LockedWasmResolverApi implements ResolverApi {
  private final ResolverApi resolverApi;
  private boolean isConsumed = false;
  private final ReadWriteLock wasmLock = new ReentrantReadWriteLock();

  public LockedWasmResolverApi(ResolverApi resolverApi) {
    this.resolverApi = resolverApi;
  }

  @Override
  public CompletableFuture<ResolveFlagsResponse> resolve(ResolveWithStickyRequest request)
      throws IsClosedException {
    if (!wasmLock.writeLock().tryLock() || isConsumed) {
      throw new IsClosedException();
    }
    try {
      return resolverApi.resolve(request);
    } finally {
      wasmLock.writeLock().unlock();
    }
  }

  @Override
  public void flush() {
    wasmLock.readLock().lock();
    try {
      resolverApi.flush();
      isConsumed = true;
    } finally {
      wasmLock.readLock().unlock();
    }
  }
}
