package com.spotify.confidence;

interface EventSenderEngine {
  void send(String name, ConfidenceValue.Struct message, ConfidenceValue.Struct context);

  void send(String name, ConfidenceValue.Struct context);

  void shutdown();
}