package com.spotify.confidence.flags.resolver;

import com.spotify.confidence.flags.resolver.domain.AccountClient;
import com.spotify.confidence.flags.resolver.domain.AccountState;
import com.spotify.confidence.shaded.flags.resolver.v1.ResolveFlagsRequest;
import com.spotify.confidence.shaded.flags.resolver.v1.ResolveFlagsResponse;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FlagResolverService {
  private static final Logger logger = LoggerFactory.getLogger(FlagResolverService.class);
  private static final Logger LOG = LoggerFactory.getLogger(FlagResolverService.class);

  public FlagResolverService(
      AccountState accountState,
      AccountClient accountClient,
      FlagLogger flagLogger,
      ResolveTokenConverter resolveTokenConverter,
      Supplier<Instant> timeSupplier,
      Supplier<String> resolveIdSupplier,
      Metrics metrics) {}

  public CompletableFuture<ResolveFlagsResponse> resolveFlags(ResolveFlagsRequest request) {
    return null;
  }
}
