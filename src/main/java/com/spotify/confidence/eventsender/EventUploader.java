package com.spotify.confidence.eventsender;

import java.io.Closeable;
import java.util.concurrent.CompletableFuture;

interface EventUploader extends Closeable {
  CompletableFuture<Boolean> upload(EventBatch batch);

}
