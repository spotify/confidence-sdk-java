package com.spotify.confidence;

import com.google.protobuf.Struct;
import com.spotify.confidence.events.v1.Event;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

interface EventUploader {
  static Event.Builder event(
      String name, ConfidenceValue.Struct context, Optional<ConfidenceValue.Struct> data) {
    final Map<String, ConfidenceValue> map = new HashMap<>(Map.of());
    final ConfidenceValue.Struct dataStruct = data.orElse(ConfidenceValue.Struct.EMPTY);
    map.putAll(dataStruct.asMap());
    map.put("context", context);

    return Event.newBuilder()
        .setEventDefinition(EventSenderEngineImpl.EVENT_NAME_PREFIX + name)
        .setPayload(Struct.newBuilder().putAllFields(ConfidenceValue.of(map).asProtoMap()));
  }

  CompletableFuture<Boolean> upload(List<Event> events);
}
