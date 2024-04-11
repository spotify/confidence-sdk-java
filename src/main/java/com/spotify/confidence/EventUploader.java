package com.spotify.confidence;

import com.spotify.confidence.events.v1.Event;
import java.util.List;
import java.util.concurrent.CompletableFuture;

interface EventUploader {
  CompletableFuture<Boolean> upload(List<Event> events);
}
