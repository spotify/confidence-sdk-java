package com.spotify.confidence.eventsender;

import java.io.Closeable;

interface EventSenderEngine extends Closeable {
  void send(String name, ConfidenceValue.Struct context);

  void send(String name, ConfidenceValue.Struct message, ConfidenceValue.Struct context);
}
