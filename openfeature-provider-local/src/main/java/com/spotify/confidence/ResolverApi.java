package com.spotify.confidence;

import com.spotify.confidence.flags.resolver.v1.ResolveWithStickyRequest;
import com.spotify.confidence.shaded.flags.resolver.v1.ResolveFlagsResponse;
import java.util.concurrent.CompletableFuture;

interface ResolverApi {
  CompletableFuture<ResolveFlagsResponse> resolve(ResolveWithStickyRequest request)
      throws IsClosedException;

  void flush();
}
