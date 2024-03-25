package com.spotify.confidence;

import com.spotify.confidence.shaded.flags.resolver.v1.ResolveFlagsResponse;
import java.io.Closeable;
import java.util.concurrent.CompletableFuture;

interface FlagResolverClient extends Closeable {
  CompletableFuture<ResolveFlagsResponse> resolveFlags(String flag, ConfidenceValue.Struct context);
}
