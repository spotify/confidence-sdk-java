package com.spotify.confidence.eventsender;

import java.util.List;

interface EventSenderStorage {
  void write(Event event);

  void batch();

  List<EventBatch> readyEvents();

  void deleteBatch(String batchId);
}
