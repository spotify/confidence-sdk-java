package com.spotify.confidence;

import com.spotify.confidence.shaded.flags.resolver.v1.WriteFlagLogsRequest;

interface WasmFlagLogger {
  void write(WriteFlagLogsRequest request);

  void shutdown();
}
