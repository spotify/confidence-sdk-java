package com.spotify.confidence.eventsender;

import java.io.Closeable;
import java.util.List;
import java.util.concurrent.CompletableFuture;

interface EventUploader extends Closeable {

  /*
   Returns the list of events to be re-tried, if any
  */
  CompletableFuture<List<Event>> upload(EventBatch batch);
}
