package com.spotify.confidence;

public sealed interface StickyResolveStrategy
    permits MaterializationRepository, ResolverFallback, ConfidenceResolverFallback {
  void close();
}
