package com.spotify.confidence;

import com.spotify.confidence.ConfidenceValue.Struct;
import com.spotify.confidence.shaded.flags.resolver.v1.ResolveFlagsResponse;
import java.io.Closeable;
import java.util.concurrent.CompletableFuture;

interface FlagResolverClient extends Closeable {
  CompletableFuture<ResolveFlagsResponse> resolveFlags(
      String flag, Struct context, String providerId);

  default CompletableFuture<ResolveFlagsResponse> resolveFlags(String flag, String providerId) {
    return resolveFlags(flag, ConfidenceValue.Struct.builder().build(), providerId);
  }
}
