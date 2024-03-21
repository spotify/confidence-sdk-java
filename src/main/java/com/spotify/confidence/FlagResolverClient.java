package com.spotify.confidence;

import com.google.common.util.concurrent.ListenableFuture;
import com.spotify.confidence.shaded.flags.resolver.v1.ResolveFlagsResponse;
import java.io.Closeable;

interface FlagResolver extends Closeable {
  ListenableFuture<ResolveFlagsResponse> resolveFlags(String flag, ConfidenceValue.Struct context);
}
