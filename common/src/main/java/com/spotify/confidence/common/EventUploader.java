package com.spotify.confidence.common;

import com.google.protobuf.Struct;
import com.spotify.confidence.events.v1.Event;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public interface EventUploader {
  public static Event.Builder event(
      String name, ConfidenceValue.Struct context, Optional<ConfidenceValue.Struct> message) {
    return Event.newBuilder()
        .setEventDefinition(EventSenderEngineImpl.EVENT_NAME_PREFIX + name)
        .setPayload(
            Struct.newBuilder()
                .putAllFields(context.asProtoMap())
                .putAllFields(message.orElse(ConfidenceValue.Struct.EMPTY).asProtoMap()));
  }

  CompletableFuture<Boolean> upload(List<Event> events);
}
