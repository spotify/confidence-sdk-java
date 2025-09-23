package com.spotify.confidence.sticky;

public sealed interface StickyResolveStrategy permits MaterializationRepository, ResolverFallback {}
