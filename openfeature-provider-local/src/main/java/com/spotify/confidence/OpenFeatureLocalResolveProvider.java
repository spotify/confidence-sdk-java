package com.spotify.confidence;

import com.google.common.annotations.VisibleForTesting;
import com.google.protobuf.Struct;
import com.spotify.confidence.shaded.flags.resolver.v1.ResolveFlagsRequest;
import com.spotify.confidence.shaded.flags.resolver.v1.ResolveFlagsResponse;
import com.spotify.confidence.shaded.flags.resolver.v1.ResolvedFlag;
import dev.openfeature.sdk.EvaluationContext;
import dev.openfeature.sdk.FeatureProvider;
import dev.openfeature.sdk.Metadata;
import dev.openfeature.sdk.ProviderEvaluation;
import dev.openfeature.sdk.Value;
import dev.openfeature.sdk.exceptions.FlagNotFoundError;
import dev.openfeature.sdk.exceptions.GeneralError;
import dev.openfeature.sdk.exceptions.TypeMismatchError;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import org.slf4j.Logger;

/**
 * OpenFeature provider for Confidence feature flags using local resolution.
 *
 * <p>This provider evaluates feature flags locally using either a WebAssembly (WASM) resolver or a
 * pure Java implementation. It periodically syncs flag configurations from the Confidence service
 * and caches them locally for fast, low-latency flag evaluation.
 *
 * <p>The provider supports two resolution modes:
 *
 * <ul>
 *   <li><strong>WASM mode</strong> (default): Uses a WebAssembly resolver
 *   <li><strong>Java mode</strong>: Uses a pure Java resolver
 * </ul>
 *
 * <p>Resolution mode can be controlled via the {@code LOCAL_RESOLVE_MODE} environment variable:
 *
 * <ul>
 *   <li>{@code LOCAL_RESOLVE_MODE=WASM} - Forces WASM mode
 *   <li>{@code LOCAL_RESOLVE_MODE=JAVA} - Forces Java mode
 *   <li>Not set - Defaults to WASM mode
 * </ul>
 *
 * <p><strong>Usage Example:</strong>
 *
 * <pre>{@code
 * // Create API credentials
 * ApiSecret apiSecret = new ApiSecret("your-client-id", "your-client-secret");
 * String clientSecret = "your-application-client-secret";
 *
 * // Create provider with default settings (exposure logs enabled)
 * OpenFeatureLocalResolveProvider provider =
 *     new OpenFeatureLocalResolveProvider(apiSecret, clientSecret);
 *
 * // Register with OpenFeature
 * OpenFeatureAPI.getInstance().setProvider(provider);
 *
 * // Use with OpenFeature client
 * Client client = OpenFeatureAPI.getInstance().getClient();
 * String flagValue = client.getStringValue("my-flag", "default-value");
 * }</pre>
 *
 * @since 0.2.4
 */
@Experimental
public class OpenFeatureLocalResolveProvider implements FeatureProvider {
  private final String clientSecret;
  private static final Logger log =
      org.slf4j.LoggerFactory.getLogger(OpenFeatureLocalResolveProvider.class);
  private final FlagResolverService flagResolverService;
  private final StickyResolveStrategy stickyResolveStrategy;

  /**
   * Creates a new OpenFeature provider for local flag resolution with default fallback strategy.
   *
   * <p>This constructor uses {@link RemoteResolverFallback} as the default sticky resolve strategy,
   * which provides fallback to the remote Confidence service when the WASM resolver encounters
   * missing materializations.
   *
   * <p>The provider will automatically determine the resolution mode (WASM or Java) based on the
   * {@code LOCAL_RESOLVE_MODE} environment variable, defaulting to WASM mode.
   *
   * @param apiSecret the API credentials containing client ID and client secret for authenticating
   *     with the Confidence service. Create using {@code new ApiSecret("client-id",
   *     "client-secret")}
   * @param clientSecret the client secret for your application, used for flag resolution
   *     authentication. This is different from the API secret and is specific to your application
   *     configuration
   * @since 0.2.4
   */
  public OpenFeatureLocalResolveProvider(ApiSecret apiSecret, String clientSecret) {
    this(apiSecret, clientSecret, new RemoteResolverFallback());
  }

  /**
   * Creates a new OpenFeature provider for local flag resolution with configurable exposure
   * logging.
   *
   * <p>This is the primary constructor that allows full control over the provider configuration.
   * The provider will automatically determine the resolution mode (WASM or Java) based on the
   * {@code LOCAL_RESOLVE_MODE} environment variable, defaulting to WASM mode.
   *
   * @param apiSecret the API credentials containing client ID and client secret for authenticating
   *     with the Confidence service. Create using {@code new ApiSecret("client-id",
   *     "client-secret")}
   * @param clientSecret the client secret for your application, used for flag resolution
   *     authentication. This is different from the API secret and is specific to your application
   *     configuration
   * @param stickyResolveStrategy the strategy to use for handling sticky flag resolution
   * @since 0.2.4
   */
  public OpenFeatureLocalResolveProvider(
      ApiSecret apiSecret, String clientSecret, StickyResolveStrategy stickyResolveStrategy) {
    final var env = System.getenv("LOCAL_RESOLVE_MODE");
    if (env != null && env.equals("WASM")) {
      this.flagResolverService =
          LocalResolverServiceFactory.from(apiSecret, clientSecret, true, stickyResolveStrategy);
    } else if (env != null && env.equals("JAVA")) {
      this.flagResolverService =
          LocalResolverServiceFactory.from(apiSecret, clientSecret, false, stickyResolveStrategy);
    } else {
      this.flagResolverService =
          LocalResolverServiceFactory.from(apiSecret, clientSecret, true, stickyResolveStrategy);
    }
    this.stickyResolveStrategy = stickyResolveStrategy;
    this.clientSecret = clientSecret;
  }

  /**
   * To be used for testing purposes only! This constructor allows to inject flags state for testing
   * the WASM resolver (no Java supported) No resolve/assign logging is forwarded to production No
   * need to supply ApiSecret
   *
   * @param accountStateProvider a functional interface that provides AccountState instances
   * @param clientSecret the flag client key used to filter the flags
   * @since 0.2.4
   */
  @VisibleForTesting
  public OpenFeatureLocalResolveProvider(
      AccountStateProvider accountStateProvider,
      String accountId,
      String clientSecret,
      StickyResolveStrategy stickyResolveStrategy) {
    this.stickyResolveStrategy = stickyResolveStrategy;
    this.clientSecret = clientSecret;
    this.flagResolverService =
        LocalResolverServiceFactory.from(accountStateProvider, accountId, stickyResolveStrategy);
  }

  @Override
  public Metadata getMetadata() {
    return () -> "confidence-sdk-java-local";
  }

  @Override
  public ProviderEvaluation<Boolean> getBooleanEvaluation(
      String key, Boolean defaultValue, EvaluationContext ctx) {
    return getCastedEvaluation(key, defaultValue, ctx, Value::asBoolean);
  }

  @Override
  public ProviderEvaluation<String> getStringEvaluation(
      String key, String defaultValue, EvaluationContext ctx) {
    return getCastedEvaluation(key, defaultValue, ctx, Value::asString);
  }

  @Override
  public ProviderEvaluation<Integer> getIntegerEvaluation(
      String key, Integer defaultValue, EvaluationContext ctx) {
    return getCastedEvaluation(key, defaultValue, ctx, Value::asInteger);
  }

  @Override
  public ProviderEvaluation<Double> getDoubleEvaluation(
      String key, Double defaultValue, EvaluationContext ctx) {
    return getCastedEvaluation(key, defaultValue, ctx, Value::asDouble);
  }

  private <T> ProviderEvaluation<T> getCastedEvaluation(
      String key, T defaultValue, EvaluationContext ctx, Function<Value, T> cast) {
    final Value wrappedDefaultValue;
    try {
      wrappedDefaultValue = new Value(defaultValue);
    } catch (InstantiationException e) {
      // this is not going to happen because we only call the constructor with supported types
      throw new RuntimeException(e);
    }

    final ProviderEvaluation<Value> objectEvaluation =
        getObjectEvaluation(key, wrappedDefaultValue, ctx);

    final T castedValue = cast.apply(objectEvaluation.getValue());
    if (castedValue == null) {
      log.warn("Cannot cast value '{}' to expected type", objectEvaluation.getValue().toString());
      throw new TypeMismatchError(
          String.format("Cannot cast value '%s' to expected type", objectEvaluation.getValue()));
    }

    return ProviderEvaluation.<T>builder()
        .value(castedValue)
        .variant(objectEvaluation.getVariant())
        .reason(objectEvaluation.getReason())
        .errorMessage(objectEvaluation.getErrorMessage())
        .errorCode(objectEvaluation.getErrorCode())
        .build();
  }

  @Override
  public void shutdown() {
    this.stickyResolveStrategy.close();
    this.flagResolverService.close();
    FeatureProvider.super.shutdown();
  }

  @Override
  public ProviderEvaluation<Value> getObjectEvaluation(
      String key, Value defaultValue, EvaluationContext ctx) {

    final FlagPath flagPath;
    try {
      flagPath = FlagPath.getPath(key);
    } catch (Exceptions.IllegalValuePath e) {
      log.warn(e.getMessage());
      throw new RuntimeException(e);
    }

    final Struct evaluationContext = OpenFeatureUtils.convertToProto(ctx);
    // resolve the flag by calling the resolver API
    final ResolveFlagsResponse resolveFlagResponse;
    try {
      final String requestFlagName = "flags/" + flagPath.getFlag();

      final var req =
          ResolveFlagsRequest.newBuilder()
              .addFlags(requestFlagName)
              .setApply(true)
              .setClientSecret(clientSecret)
              .setEvaluationContext(
                  Struct.newBuilder().putAllFields(evaluationContext.getFieldsMap()).build())
              .build();

      resolveFlagResponse = flagResolverService.resolveFlags(req).get();

      if (resolveFlagResponse.getResolvedFlagsList().isEmpty()) {
        log.warn("No active flag '{}' was found", flagPath.getFlag());
        throw new FlagNotFoundError(
            String.format("No active flag '%s' was found", flagPath.getFlag()));
      }

      final String responseFlagName = resolveFlagResponse.getResolvedFlags(0).getFlag();
      if (!requestFlagName.equals(responseFlagName)) {
        log.warn("Unexpected flag '{}' from remote", responseFlagName.replaceFirst("^flags/", ""));
        throw new FlagNotFoundError(
            String.format(
                "Unexpected flag '%s' from remote", responseFlagName.replaceFirst("^flags/", "")));
      }

      final ResolvedFlag resolvedFlag = resolveFlagResponse.getResolvedFlags(0);

      if (resolvedFlag.getVariant().isEmpty()) {
        log.debug(
            String.format(
                "The server returned no assignment for the flag '%s'. Typically, this happens "
                    + "if no configured rules matches the given evaluation context.",
                flagPath.getFlag()));
        return ProviderEvaluation.<Value>builder()
            .value(defaultValue)
            .reason(
                "The server returned no assignment for the flag. Typically, this happens "
                    + "if no configured rules matches the given evaluation context.")
            .build();
      } else {
        final Value fullValue =
            OpenFeatureTypeMapper.from(resolvedFlag.getValue(), resolvedFlag.getFlagSchema());

        // if a path is given, extract expected portion from the structured value
        Value value = OpenFeatureUtils.getValueForPath(flagPath.getPath(), fullValue);

        if (value.isNull()) {
          value = defaultValue;
        }

        // regular resolve was successful
        return ProviderEvaluation.<Value>builder()
            .value(value)
            .reason(resolvedFlag.getReason().toString())
            .variant(resolvedFlag.getVariant())
            .build();
      }
    } catch (StatusRuntimeException e) {
      handleStatusRuntimeException(e);
      throw new GeneralError("Unknown error occurred when calling the provider backend");
    } catch (ExecutionException | InterruptedException e) {
      throw new RuntimeException(e);
    }
  }

  private static void handleStatusRuntimeException(StatusRuntimeException e) {
    if (e.getStatus().getCode() == Status.Code.DEADLINE_EXCEEDED) {
      log.error("Deadline exceeded when calling provider backend", e);
      throw new GeneralError("Deadline exceeded when calling provider backend");
    } else if (e.getStatus().getCode() == Status.Code.UNAVAILABLE) {
      log.error("Provider backend is unavailable", e);
      throw new GeneralError("Provider backend is unavailable");
    } else if (e.getStatus().getCode() == Status.Code.UNAUTHENTICATED) {
      log.error("UNAUTHENTICATED", e);
      throw new GeneralError("UNAUTHENTICATED");
    } else {
      log.error(
          "Unknown error occurred when calling the provider backend. Grpc status code {}",
          e.getStatus().getCode(),
          e);
      throw new GeneralError(
          String.format(
              "Unknown error occurred when calling the provider backend. Exception: %s",
              e.getMessage()));
    }
  }
}
