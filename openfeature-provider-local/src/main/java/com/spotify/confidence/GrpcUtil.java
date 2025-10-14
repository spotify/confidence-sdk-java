package com.spotify.confidence;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Utility class for converting gRPC ListenableFuture to Java CompletableFuture. Copied from the
 * main SDK to avoid dependencies.
 */
final class GrpcUtil {

  private static final String CONFIDENCE_DOMAIN = "edge-grpc.spotify.com";

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

  static ManagedChannel createConfidenceChannel() {
    final String confidenceDomain =
        Optional.ofNullable(System.getenv("CONFIDENCE_DOMAIN")).orElse(CONFIDENCE_DOMAIN);
    final boolean useGrpcPlaintext =
        Optional.ofNullable(System.getenv("CONFIDENCE_GRPC_PLAINTEXT"))
            .map(Boolean::parseBoolean)
            .orElse(false);
    ManagedChannelBuilder<?> builder = ManagedChannelBuilder.forTarget(confidenceDomain);
    if (useGrpcPlaintext) {
      builder = builder.usePlaintext();
    }
    return builder.intercept(new DefaultDeadlineClientInterceptor(Duration.ofMinutes(1))).build();
  }
}
