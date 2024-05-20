package com.spotify.confidence;

import java.io.Closeable;
import java.util.Optional;

interface EventSenderEngine extends Closeable {
  void send(String name, ConfidenceValue.Struct context, Optional<ConfidenceValue.Struct> message);
}
