package com.spotify.confidence;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import java.util.concurrent.CompletableFuture;

/**
 * Utility class for converting gRPC ListenableFuture to Java CompletableFuture. Copied from the
 * main SDK to avoid dependencies.
 */
final class GrpcUtil {

  private GrpcUtil() {}

  static <T> CompletableFuture<T> toCompletableFuture(final ListenableFuture<T> listenableFuture) {
    final CompletableFuture<T> completableFuture =
        new CompletableFuture<>() {
          @Override
          public boolean cancel(boolean mayInterruptIfRunning) {
            listenableFuture.cancel(mayInterruptIfRunning);
            return super.cancel(mayInterruptIfRunning);
          }
        };
    Futures.addCallback(
        listenableFuture,
        new FutureCallback<T>() {
          @Override
          public void onSuccess(T result) {
            completableFuture.complete(result);
          }

          @Override
          public void onFailure(Throwable t) {
            completableFuture.completeExceptionally(t);
          }
        },
        MoreExecutors.directExecutor());
    return completableFuture;
  }
}
