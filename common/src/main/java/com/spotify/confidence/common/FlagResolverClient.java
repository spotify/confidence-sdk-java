package com.spotify.confidence.common;

import com.spotify.confidence.common.ConfidenceValue.Struct;
import com.spotify.confidence.shaded.flags.resolver.v1.ResolveFlagsResponse;
import java.io.Closeable;
import java.util.concurrent.CompletableFuture;

public interface FlagResolverClient extends Closeable {
  CompletableFuture<ResolveFlagsResponse> resolveFlags(String flag, Struct context);
}
