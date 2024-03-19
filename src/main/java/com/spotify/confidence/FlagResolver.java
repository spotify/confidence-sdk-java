package com.spotify.confidence;

import com.spotify.confidence.shaded.flags.resolver.v1.ResolveFlagsResponse;
import java.io.Closeable;

interface FlagResolver extends Closeable {
  ResolveFlagsResponse resolveFlags(String flag, ConfidenceValue.Struct context);
}
