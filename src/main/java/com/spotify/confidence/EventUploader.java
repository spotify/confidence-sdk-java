package com.spotify.confidence;

import static com.spotify.confidence.GrpcEventUploader.CONTEXT;

import com.google.protobuf.Struct;
import com.spotify.confidence.events.v1.Event;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

interface EventUploader {
  static Event.Builder event(
      String name, ConfidenceValue.Struct context, Optional<ConfidenceValue.Struct> message) {
    return Event.newBuilder()
        .setEventDefinition(EventSenderEngineImpl.EVENT_NAME_PREFIX + name)
        .setPayload(
            Struct.newBuilder()
                .putAllFields(message.orElse(ConfidenceValue.Struct.EMPTY).asProtoMap())
                .putFields(CONTEXT, context.toProto()));
  }

  CompletableFuture<Boolean> upload(List<Event> events);
}
