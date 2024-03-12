package com.spotify.confidence;

import java.util.concurrent.CompletableFuture;

interface EventUploader {
  CompletableFuture<Boolean> upload(EventBatch event);
}