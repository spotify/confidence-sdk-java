package com.spotify.confidence.common;

import java.io.Closeable;
import java.util.Optional;

public interface EventSenderEngine extends Closeable {
  void emit(String name, ConfidenceValue.Struct context, Optional<ConfidenceValue.Struct> message);
}
