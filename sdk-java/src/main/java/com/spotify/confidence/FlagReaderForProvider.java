package com.spotify.confidence;

import com.spotify.confidence.shaded.flags.resolver.v1.ResolveFlagsResponse;
import java.io.Closeable;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public interface FlagReaderForProvider extends Contextual, Closeable {
  CompletableFuture<ResolveFlagsResponse> resolveFlags(String flagName, String providerId);

  FlagReaderForProvider withContext(ConfidenceValue.Struct context);

  default FlagReaderForProvider withContext(Map<String, ConfidenceValue> context) {
    return withContext(ConfidenceValue.Struct.of(context));
  }
}
