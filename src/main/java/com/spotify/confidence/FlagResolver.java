package com.spotify.confidence;

import com.google.protobuf.Struct;
import com.spotify.confidence.shaded.flags.resolver.v1.ResolveFlagsResponse;
import java.util.concurrent.CompletableFuture;

public interface FlagResolver {
  void close();

  public CompletableFuture<ResolveFlagsResponse> resolve(String flag, Struct context);
}
