package com.spotify.confidence.sticky;

import com.spotify.confidence.shaded.flags.resolver.v1.ResolveFlagsRequest;
import com.spotify.confidence.shaded.flags.resolver.v1.ResolveFlagsResponse;

public non-sealed interface ResolverFallback extends StickyResolveStrategy {
  ResolveFlagsResponse resolve(ResolveFlagsRequest request);
}
