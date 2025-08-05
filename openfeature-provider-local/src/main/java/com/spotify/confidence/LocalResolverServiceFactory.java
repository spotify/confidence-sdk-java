package com.spotify.confidence;

import com.codahale.metrics.MetricRegistry;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.protobuf.Struct;
import com.spotify.confidence.flags.shaded.admin.v1.FlagAdminServiceGrpc;
import com.spotify.confidence.flags.shaded.admin.v1.ResolverStateServiceGrpc;
import com.spotify.confidence.shaded.flags.resolver.v1.InternalFlagLoggerServiceGrpc;
import com.spotify.confidence.shaded.flags.resolver.v1.Sdk;
import com.spotify.confidence.shaded.iam.v1.AuthServiceGrpc;
import com.spotify.confidence.shaded.iam.v1.ClientCredential;
import io.grpc.Channel;
import io.grpc.ClientInterceptors;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.protobuf.services.HealthStatusManager;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.apache.commons.lang3.RandomStringUtils;

class LocalResolverServiceFactory implements ResolverServiceFactory {

  private final AtomicReference<ResolverState> resolverStateHolder;
  private final ResolveTokenConverter resolveTokenConverter;

  private final Supplier<Instant> timeSupplier;
  private final Supplier<String> resolveIdSupplier;
  private final ResolveLogger resolveLogger;
  private final AssignLogger assignLogger;
  private static final MetricRegistry metricRegistry = new MetricRegistry();
  private static final String CONFIDENCE_DOMAIN = "edge-grpc.spotify.com";
  private static final Duration ASSIGN_LOG_INTERVAL = Duration.ofSeconds(10);
  private static final ScheduledExecutorService flagsFetcherExecutor =
      Executors.newScheduledThreadPool(1, new ThreadFactoryBuilder().setDaemon(true).build());
  private static final Duration RESOLVE_INFO_LOG_INTERVAL = Duration.ofMinutes(1);

  private static ManagedChannel createConfidenceChannel() {
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

  static FlagResolverService from(ApiSecret apiSecret, String clientSecret, boolean isWasm) {
    if (isWasm) {
      return createWasmFlagResolverService(apiSecret);
    }
    return createJavaFlagResolverService(apiSecret, clientSecret);
  }

  private static FlagResolverService createWasmFlagResolverService(ApiSecret apiSecret) {
    final var channel = createConfidenceChannel();
    final AuthServiceGrpc.AuthServiceBlockingStub authService =
        AuthServiceGrpc.newBlockingStub(channel);
    final TokenHolder tokenHolder =
        new TokenHolder(apiSecret.clientId(), apiSecret.clientSecret(), authService);
    final TokenHolder.Token token = tokenHolder.getToken();
    final Channel authenticatedChannel =
        ClientInterceptors.intercept(channel, new JwtAuthClientInterceptor(tokenHolder));
    final ResolverStateServiceGrpc.ResolverStateServiceBlockingStub resolverStateService =
        ResolverStateServiceGrpc.newBlockingStub(authenticatedChannel);
    final HealthStatusManager healthStatusManager = new HealthStatusManager();
    final HealthStatus healthStatus = new HealthStatus(healthStatusManager);
    final FlagsAdminStateFetcher fetcher =
        new FlagsAdminStateFetcher(resolverStateService, healthStatus, token.account());
    final long pollIntervalSeconds =
        Optional.ofNullable(System.getenv("CONFIDENCE_RESOLVER_POLL_INTERVAL_SECONDS"))
            .map(Long::parseLong)
            .orElse(Duration.ofMinutes(5).toSeconds());

    fetcher.reload();
    final var wasmResolverApi = new WasmResolveApi();
    wasmResolverApi.setResolverState(fetcher.rawStateHolder().get().toByteArray());
    flagsFetcherExecutor.scheduleWithFixedDelay(
        () -> {
          fetcher.reload();
          wasmResolverApi.setResolverState(fetcher.rawStateHolder().get().toByteArray());
        },
        pollIntervalSeconds,
        pollIntervalSeconds,
        TimeUnit.SECONDS);
    return request -> CompletableFuture.completedFuture(wasmResolverApi.resolve(request));
  }

  private static FlagResolverService createJavaFlagResolverService(
      ApiSecret apiSecret, String clientSecret) {
    final var channel = createConfidenceChannel();
    final AuthServiceGrpc.AuthServiceBlockingStub authService =
        AuthServiceGrpc.newBlockingStub(channel);
    final TokenHolder tokenHolder =
        new TokenHolder(apiSecret.clientId(), apiSecret.clientSecret(), authService);
    final TokenHolder.Token token = tokenHolder.getToken();
    final Channel authenticatedChannel =
        ClientInterceptors.intercept(channel, new JwtAuthClientInterceptor(tokenHolder));
    final var flagLoggerStub = InternalFlagLoggerServiceGrpc.newBlockingStub(authenticatedChannel);
    final long assignLogCapacity =
        Optional.ofNullable(System.getenv("CONFIDENCE_ASSIGN_LOG_CAPACITY"))
            .map(Long::parseLong)
            .orElseGet(() -> (long) (Runtime.getRuntime().maxMemory() / 3.0));
    final ResolverStateServiceGrpc.ResolverStateServiceBlockingStub resolverStateService =
        ResolverStateServiceGrpc.newBlockingStub(authenticatedChannel);
    final HealthStatusManager healthStatusManager = new HealthStatusManager();
    final HealthStatus healthStatus = new HealthStatus(healthStatusManager);
    final FlagsAdminStateFetcher sidecarFlagsAdminFetcher =
        new FlagsAdminStateFetcher(resolverStateService, healthStatus, token.account());
    final long pollIntervalSeconds =
        Optional.ofNullable(System.getenv("CONFIDENCE_RESOLVER_POLL_INTERVAL_SECONDS"))
            .map(Long::parseLong)
            .orElse(Duration.ofMinutes(5).toSeconds());

    final ResolveTokenConverter resolveTokenConverter = new PlainResolveTokenConverter();
    sidecarFlagsAdminFetcher.reload();
    flagsFetcherExecutor.scheduleWithFixedDelay(
        sidecarFlagsAdminFetcher::reload,
        pollIntervalSeconds,
        pollIntervalSeconds,
        TimeUnit.SECONDS);
    final AssignLogger assignLogger =
        AssignLogger.createStarted(
            flagLoggerStub, ASSIGN_LOG_INTERVAL, metricRegistry, assignLogCapacity);
    final var flagsAdminStub = FlagAdminServiceGrpc.newBlockingStub(authenticatedChannel);
    final ResolveLogger resolveLogger =
        ResolveLogger.createStarted(() -> flagsAdminStub, RESOLVE_INFO_LOG_INTERVAL);
    return new LocalResolverServiceFactory(
            sidecarFlagsAdminFetcher.stateHolder(),
            resolveTokenConverter,
            resolveLogger,
            assignLogger)
        .create(clientSecret);
  }

  public LocalResolverServiceFactory(
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

  public LocalResolverServiceFactory(
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
    return createJavaFlagResolverService(clientSecret);
  }

  private FlagResolverService createJavaFlagResolverService(
      ClientCredential.ClientSecret clientSecret) {
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

    return new JavaFlagResolverService(
        accountState,
        accountClient,
        flagLogger,
        resolveTokenConverter,
        timeSupplier,
        resolveIdSupplier);
  }
}
