package com.spotify.confidence;

import java.util.List;

interface EventSenderStorage {
  void write(Event event);

  void createBatch();

  List<EventBatch> getBatches();

  void deleteBatch(String batchId);
}
