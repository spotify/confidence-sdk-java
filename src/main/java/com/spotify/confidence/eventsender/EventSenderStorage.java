package com.spotify.confidence.eventsender;

import java.util.List;

interface EventSenderStorage {
  void write(Event event);

  void createBatch();

  List<EventBatch> getBatches();

  void deleteBatch(String batchId);
}
