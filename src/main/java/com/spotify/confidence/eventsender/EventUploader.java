package com.spotify.confidence.eventsender;

import java.util.concurrent.CompletableFuture;

interface EventUploader {
  CompletableFuture<Boolean> upload(EventBatch event);
}
