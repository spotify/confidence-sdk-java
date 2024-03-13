package com.spotify.confidence.eventsender;

import java.io.Closeable;

interface EventSenderEngine extends Closeable {
  void send(String name, Value.Struct context);

  void send(String name, Value.Struct message, Value.Struct context);
}
