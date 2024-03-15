package com.spotify.confidence.eventsender;

import java.util.List;

interface EventSenderStorage {
  void write(Event event);

  List<EventBatch> createBatch();

  void deleteBatch(String batchId);
}
