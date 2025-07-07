package com.spotify.confidence.flags.resolver;

import com.google.protobuf.Struct;
import com.spotify.confidence.UnauthenticatedException;
import com.spotify.confidence.flags.resolver.domain.AccountClient;
import com.spotify.confidence.flags.resolver.domain.AccountState;
import com.spotify.confidence.flags.resolver.domain.FlagToApply;
import com.spotify.confidence.flags.resolver.domain.ResolvedValue;
import com.spotify.confidence.flags.resolver.domain.ResolverState;
import com.spotify.confidence.shaded.flags.resolver.v1.Sdk;
import com.spotify.confidence.shaded.iam.v1.ClientCredential;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.apache.commons.lang3.RandomStringUtils;

public class SidecarResolverServiceFactory implements ResolverServiceFactory {

  private final AtomicReference<ResolverState> resolverStateHolder;
  private final ResolveTokenConverter resolveTokenConverter;

  private final Supplier<Instant> timeSupplier;
  private final Supplier<String> resolveIdSupplier;
  private final ResolveLogger resolveLogger;
  private final AssignLogger assignLogger;

  public SidecarResolverServiceFactory(
      AtomicReference<ResolverState> resolverStateHolder,
      ResolveTokenConverter resolveTokenConverter,
      ResolveLogger resolveLogger,
      AssignLogger assignLogger) {
    this(
        resolverStateHolder,
        resolveTokenConverter,
        Instant::now,
        () -> RandomStringUtils.randomAlphanumeric(32),
        resolveLogger,
        assignLogger);
  }

  public SidecarResolverServiceFactory(
      AtomicReference<ResolverState> resolverStateHolder,
      ResolveTokenConverter resolveTokenConverter,
      Supplier<Instant> timeSupplier,
      Supplier<String> resolveIdSupplier,
      ResolveLogger resolveLogger,
      AssignLogger assignLogger) {
    this.resolverStateHolder = resolverStateHolder;
    this.resolveTokenConverter = resolveTokenConverter;
    this.timeSupplier = timeSupplier;
    this.resolveIdSupplier = resolveIdSupplier;
    this.resolveLogger = resolveLogger;
    this.assignLogger = assignLogger;
  }

  @Override
  public FlagResolverService create(ClientCredential.ClientSecret clientSecret) {
    final ResolverState state = resolverStateHolder.get();

    final AccountClient accountClient = state.secrets().get(clientSecret);
    if (accountClient == null) {
      throw new UnauthenticatedException("Not authenticated");
    }

    final AccountState accountState = state.accountStates().get(accountClient.accountName());
    final FlagLogger flagLogger =
        new FlagLogger() {
          @Override
          public void logResolve(
              String resolveId,
              Struct evaluationContext,
              Sdk sdk,
              AccountClient accountClient,
              List<ResolvedValue> values) {
            resolveLogger.logResolve(resolveId, evaluationContext, accountClient, values);
          }

          @Override
          public void logAssigns(
              String resolveId,
              Sdk sdk,
              List<FlagToApply> flagsToApply,
              AccountClient accountClient) {
            assignLogger.logAssigns(resolveId, sdk, flagsToApply, accountClient);
          }
        };

    return new FlagResolverService(
        accountState,
        accountClient,
        flagLogger,
        resolveTokenConverter,
        timeSupplier,
        resolveIdSupplier);
  }
}
