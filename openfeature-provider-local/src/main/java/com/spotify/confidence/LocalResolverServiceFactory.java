package com.spotify.confidence;

import com.codahale.metrics.MetricRegistry;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.protobuf.Struct;
import com.spotify.confidence.shaded.flags.admin.v1.FlagAdminServiceGrpc;
import com.spotify.confidence.shaded.flags.admin.v1.ResolverStateServiceGrpc;
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

  private final SwapWasmResolverApi wasmResolveApi;
  private final Supplier<Instant> timeSupplier;
  private final Supplier<String> resolveIdSupplier;
  private final ResolveLogger resolveLogger;
  private final AssignLogger assignLogger;
  private final boolean enableExposureLogs;
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

  static FlagResolverService from(
      ApiSecret apiSecret, String clientSecret, boolean isWasm, boolean enableExposureLogs) {
    return createFlagResolverService(apiSecret, clientSecret, isWasm, enableExposureLogs);
  }

  static FlagResolverService from(
      AccountStateProvider accountStateProvider, String clientSecret, boolean enableExposureLogs) {
    final var env = System.getenv("LOCAL_RESOLVE_MODE");
    final boolean isWasm = env == null || !env.equals("JAVA"); // Default to WASM
    return createFlagResolverService(
        accountStateProvider, clientSecret, isWasm, enableExposureLogs);
  }

  private static FlagResolverService createFlagResolverService(
      ApiSecret apiSecret, String clientSecret, boolean isWasm, boolean enableExposureLogs) {
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

    final var flagsAdminStub = FlagAdminServiceGrpc.newBlockingStub(authenticatedChannel);
    final AssignLogger assignLogger =
        AssignLogger.createStarted(
            flagLoggerStub, ASSIGN_LOG_INTERVAL, metricRegistry, assignLogCapacity);
    final ResolveLogger resolveLogger =
        ResolveLogger.createStarted(() -> flagsAdminStub, RESOLVE_INFO_LOG_INTERVAL);
    final var flagLogger = getFlagLogger(resolveLogger, assignLogger, enableExposureLogs);
    if (isWasm) {
      final SwapWasmResolverApi wasmResolverApi =
          new SwapWasmResolverApi(
              flagLogger, sidecarFlagsAdminFetcher.rawStateHolder().get().toByteArray());
      flagsFetcherExecutor.scheduleAtFixedRate(
          () -> {
            sidecarFlagsAdminFetcher.reload();
            wasmResolverApi.updateState(
                sidecarFlagsAdminFetcher.rawStateHolder().get().toByteArray());
          },
          pollIntervalSeconds,
          pollIntervalSeconds,
          TimeUnit.SECONDS);
      return new LocalResolverServiceFactory(
              wasmResolverApi,
              sidecarFlagsAdminFetcher.stateHolder(),
              resolveTokenConverter,
              resolveLogger,
              assignLogger,
              enableExposureLogs)
          .create(clientSecret);
    } else {
      flagsFetcherExecutor.scheduleWithFixedDelay(
          sidecarFlagsAdminFetcher::reload,
          pollIntervalSeconds,
          pollIntervalSeconds,
          TimeUnit.SECONDS);
      return new LocalResolverServiceFactory(
              sidecarFlagsAdminFetcher.stateHolder(),
              resolveTokenConverter,
              resolveLogger,
              assignLogger,
              enableExposureLogs)
          .create(clientSecret);
    }
  }

  private static FlagResolverService createFlagResolverService(
      AccountStateProvider accountStateProvider,
      String clientSecret,
      boolean isWasm,
      boolean enableExposureLogs) {
    final var channel = createConfidenceChannel();
    final long assignLogCapacity =
        Optional.ofNullable(System.getenv("CONFIDENCE_ASSIGN_LOG_CAPACITY"))
            .map(Long::parseLong)
            .orElseGet(() -> (long) (Runtime.getRuntime().maxMemory() / 3.0));
    final var flagLoggerStub = InternalFlagLoggerServiceGrpc.newBlockingStub(channel);
    final AssignLogger assignLogger =
        AssignLogger.createStarted(
            flagLoggerStub, ASSIGN_LOG_INTERVAL, metricRegistry, assignLogCapacity);
    final ResolveLogger resolveLogger =
        ResolveLogger.createStarted(
            () -> FlagAdminServiceGrpc.newBlockingStub(channel), RESOLVE_INFO_LOG_INTERVAL);
    final long pollIntervalSeconds =
        Optional.ofNullable(System.getenv("CONFIDENCE_RESOLVER_POLL_INTERVAL_SECONDS"))
            .map(Long::parseLong)
            .orElse(Duration.ofMinutes(5).toSeconds());

    final ResolveTokenConverter resolveTokenConverter = new PlainResolveTokenConverter();

    // Initialize the state holder with the first AccountState
    final AccountState initialAccountState = accountStateProvider.provide();
    final AtomicReference<ResolverState> stateHolder =
        new AtomicReference<>(
            new ResolverState(
                java.util.Map.of(initialAccountState.account().name(), initialAccountState),
                initialAccountState.secrets()));

    // For WASM mode, we also need to track the raw proto state
    final AtomicReference<com.spotify.confidence.shaded.flags.admin.v1.ResolverState>
        rawStateHolder = new AtomicReference<>(stateHolder.get().toProto());

    final var flagLogger = getFlagLogger(resolveLogger, assignLogger, enableExposureLogs);

    if (isWasm) {
      final SwapWasmResolverApi wasmResolverApi =
          new SwapWasmResolverApi(flagLogger, rawStateHolder.get().toByteArray());

      // Schedule periodic refresh of the AccountState with WASM support
      flagsFetcherExecutor.scheduleAtFixedRate(
          () -> {
            try {
              final AccountState newAccountState = accountStateProvider.provide();
              final ResolverState newResolverState =
                  new ResolverState(
                      java.util.Map.of(newAccountState.account().name(), newAccountState),
                      newAccountState.secrets());
              stateHolder.set(newResolverState);

              final com.spotify.confidence.shaded.flags.admin.v1.ResolverState newRawState =
                  newResolverState.toProto();
              rawStateHolder.set(newRawState);
              wasmResolverApi.updateState(newRawState.toByteArray());
            } catch (Exception e) {
              // Log error but don't propagate to avoid stopping the scheduler
              org.slf4j.LoggerFactory.getLogger(LocalResolverServiceFactory.class)
                  .warn("Failed to refresh AccountState from provider, ignoring refresh", e);
            }
          },
          pollIntervalSeconds,
          pollIntervalSeconds,
          TimeUnit.SECONDS);

      return new LocalResolverServiceFactory(
              wasmResolverApi,
              stateHolder,
              resolveTokenConverter,
              resolveLogger,
              assignLogger,
              enableExposureLogs)
          .create(clientSecret);
    } else {
      // Schedule periodic refresh of the AccountState for Java mode
      flagsFetcherExecutor.scheduleWithFixedDelay(
          () -> {
            try {
              final AccountState newAccountState = accountStateProvider.provide();
              final ResolverState newResolverState =
                  new ResolverState(
                      java.util.Map.of(newAccountState.account().name(), newAccountState),
                      newAccountState.secrets());
              stateHolder.set(newResolverState);
            } catch (Exception e) {
              // Log error but don't propagate to avoid stopping the scheduler
              org.slf4j.LoggerFactory.getLogger(LocalResolverServiceFactory.class)
                  .warn("Failed to refresh AccountState from provider, ignoring refresh", e);
            }
          },
          pollIntervalSeconds,
          pollIntervalSeconds,
          TimeUnit.SECONDS);

      return new LocalResolverServiceFactory(
              stateHolder, resolveTokenConverter, resolveLogger, assignLogger, enableExposureLogs)
          .create(clientSecret);
    }
  }

  LocalResolverServiceFactory(
      SwapWasmResolverApi wasmResolveApi,
      AtomicReference<ResolverState> resolverStateHolder,
      ResolveTokenConverter resolveTokenConverter,
      ResolveLogger resolveLogger,
      AssignLogger assignLogger,
      boolean enableExposureLogs) {
    this(
        wasmResolveApi,
        resolverStateHolder,
        resolveTokenConverter,
        Instant::now,
        () -> RandomStringUtils.randomAlphanumeric(32),
        resolveLogger,
        assignLogger,
        enableExposureLogs);
  }

  LocalResolverServiceFactory(
      SwapWasmResolverApi wasmResolveApi,
      AtomicReference<ResolverState> resolverStateHolder,
      ResolveTokenConverter resolveTokenConverter,
      ResolveLogger resolveLogger,
      AssignLogger assignLogger) {
    this(
        wasmResolveApi,
        resolverStateHolder,
        resolveTokenConverter,
        Instant::now,
        () -> RandomStringUtils.randomAlphanumeric(32),
        resolveLogger,
        assignLogger,
        true);
  }

  LocalResolverServiceFactory(
      AtomicReference<ResolverState> resolverStateHolder,
      ResolveTokenConverter resolveTokenConverter,
      ResolveLogger resolveLogger,
      AssignLogger assignLogger,
      boolean enableExposureLogs) {
    this(
        null,
        resolverStateHolder,
        resolveTokenConverter,
        Instant::now,
        () -> RandomStringUtils.randomAlphanumeric(32),
        resolveLogger,
        assignLogger,
        enableExposureLogs);
  }

  LocalResolverServiceFactory(
      AtomicReference<ResolverState> resolverStateHolder,
      ResolveTokenConverter resolveTokenConverter,
      ResolveLogger resolveLogger,
      AssignLogger assignLogger) {
    this(
        null,
        resolverStateHolder,
        resolveTokenConverter,
        Instant::now,
        () -> RandomStringUtils.randomAlphanumeric(32),
        resolveLogger,
        assignLogger,
        true);
  }

  LocalResolverServiceFactory(
      SwapWasmResolverApi wasmResolveApi,
      AtomicReference<ResolverState> resolverStateHolder,
      ResolveTokenConverter resolveTokenConverter,
      Supplier<Instant> timeSupplier,
      Supplier<String> resolveIdSupplier,
      ResolveLogger resolveLogger,
      AssignLogger assignLogger,
      boolean enableExposureLogs) {
    this.wasmResolveApi = wasmResolveApi;
    this.resolverStateHolder = resolverStateHolder;
    this.resolveTokenConverter = resolveTokenConverter;
    this.timeSupplier = timeSupplier;
    this.resolveIdSupplier = resolveIdSupplier;
    this.resolveLogger = resolveLogger;
    this.assignLogger = assignLogger;
    this.enableExposureLogs = enableExposureLogs;
  }

  @VisibleForTesting
  public void setState(byte[] state) {
    if (this.wasmResolveApi != null) {
      wasmResolveApi.updateState(state);
    }
  }

  @Override
  public FlagResolverService create(ClientCredential.ClientSecret clientSecret) {
    if (wasmResolveApi != null) {
      return request -> CompletableFuture.completedFuture(wasmResolveApi.resolve(request));
    }
    return createJavaFlagResolverService(clientSecret);
  }

  private FlagResolverService createJavaFlagResolverService(
      ClientCredential.ClientSecret clientSecret) {
    final ResolverState state = resolverStateHolder.get();

    final AccountClient accountClient = state.secrets().get(clientSecret);
    if (accountClient == null) {
      throw new UnauthenticatedException(
          "Resolver state not set or client secret could not be found");
    }

    final AccountState accountState = state.accountStates().get(accountClient.accountName());
    final var flagLogger = getFlagLogger(resolveLogger, assignLogger, enableExposureLogs);

    return new JavaFlagResolverService(
        accountState,
        accountClient,
        flagLogger,
        resolveTokenConverter,
        timeSupplier,
        resolveIdSupplier);
  }

  private static FlagLogger getFlagLogger(
      ResolveLogger resolveLogger, AssignLogger assignLogger, boolean enableExposureLogs) {
    if (!enableExposureLogs) {
      return new FlagLogger() {
        @Override
        public void logResolve(
            String resolveId,
            Struct evaluationContext,
            Sdk sdk,
            AccountClient accountClient,
            List<ResolvedValue> values) {
          // Logging disabled - no-op
        }

        @Override
        public void logAssigns(
            String resolveId,
            Sdk sdk,
            List<FlagToApply> flagsToApply,
            AccountClient accountClient) {
          // Logging disabled - no-op
        }
      };
    }

    return new FlagLogger() {
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
          String resolveId, Sdk sdk, List<FlagToApply> flagsToApply, AccountClient accountClient) {
        assignLogger.logAssigns(resolveId, sdk, flagsToApply, accountClient);
      }
    };
  }
}
