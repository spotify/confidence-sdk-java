package com.spotify.confidence.sticky;

import com.spotify.confidence.shaded.flags.resolver.v1.ResolveFlagsRequest;
import com.spotify.confidence.shaded.flags.resolver.v1.ResolveFlagsResponse;
import java.util.concurrent.CompletableFuture;

public non-sealed interface ResolverFallback extends StickyResolveStrategy {
  CompletableFuture<ResolveFlagsResponse> resolve(ResolveFlagsRequest request);
}
