package com.spotify.confidence;

import java.util.List;

interface EventSenderStorage {
  void write(Event event);

  int pendingEvents();

  void createBatch();

  List<EventBatch> getBatches();

  void deleteBatch(String batchId);
}
