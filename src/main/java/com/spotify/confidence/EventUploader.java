package com.spotify.confidence;

import com.spotify.confidence.events.v1.Event;
import java.io.Closeable;
import java.util.List;
import java.util.concurrent.CompletableFuture;

interface EventUploader extends Closeable {
  CompletableFuture<Boolean> upload(List<Event> events);
}
