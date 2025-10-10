package com.spotify.confidence;

import com.spotify.confidence.flags.resolver.v1.ResolveWithStickyRequest;
import com.spotify.confidence.shaded.flags.resolver.v1.ResolveFlagsResponse;
import java.util.concurrent.CompletableFuture;

/** Common interface for WASM-based flag resolver implementations. */
interface RotatingWasmResolverApi {
  /**
   * Resolves flags with sticky assignment support.
   *
   * @param request The resolve request
   * @return The resolve response
   */
  CompletableFuture<ResolveFlagsResponse> resolve(ResolveWithStickyRequest request);

  /**
   * Updates the resolver state and flushes any pending logs.
   *
   * @param state The new resolver state
   * @param accountId The account ID
   */
  void rotate(byte[] state, String accountId);

  /** Closes the resolver and releases any resources. */
  void close();
}
