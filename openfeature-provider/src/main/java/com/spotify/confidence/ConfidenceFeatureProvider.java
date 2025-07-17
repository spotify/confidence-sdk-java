package com.spotify.confidence;

import static com.spotify.confidence.FlagResolverClientImpl.OPEN_FEATURE_RESOLVE_CONTEXT_KEY;

import com.google.protobuf.Struct;
import com.spotify.confidence.Exceptions.IllegalValuePath;
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
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Status.Code;
import io.grpc.StatusRuntimeException;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import org.slf4j.Logger;

/** OpenFeature Provider for feature flagging with the Confidence platform */
public class ConfidenceFeatureProvider implements FeatureProvider {

  private final Confidence confidence;

  /**
   * ConfidenceFeatureProvider constructor
   *
   * @param confidence an instance of the Confidence
   */
  public ConfidenceFeatureProvider(Confidence confidence) {
    this.confidence = confidence;
  }

  private static final Logger log =
      org.slf4j.LoggerFactory.getLogger(ConfidenceFeatureProvider.class);

  /**
   * ConfidenceFeatureProvider constructor
   *
   * @param clientSecret generated from the Confidence portal
   * @param managedChannel gRPC channel
   * @deprecated This constructor is deprecated. Please use {@link
   *     #ConfidenceFeatureProvider(Confidence)} instead.
   */
  @Deprecated()
  public ConfidenceFeatureProvider(String clientSecret, ManagedChannel managedChannel) {
    this(Confidence.builder(clientSecret).flagResolverManagedChannel(managedChannel).build());
  }

  /**
   * ConfidenceFeatureProvider constructor
   *
   * @param clientSecret generated from the Confidence portal
   * @deprecated This constructor is deprecated. Please use {@link
   *     #ConfidenceFeatureProvider(Confidence)} instead.
   */
  @Deprecated()
  public ConfidenceFeatureProvider(String clientSecret) {
    this(clientSecret, ManagedChannelBuilder.forAddress("edge-grpc.spotify.com", 443).build());
  }

  /**
   * ConfidenceFeatureProvider constructor that allows you to override the default gRPC host and
   * port, used for local resolver.
   *
   * @param clientSecret generated from the Confidence portal
   * @param host gRPC host you want to connect to.
   * @param port port of the gRPC host that you want to use.
   * @deprecated This constructor is deprecated. Please use {@link
   *     #ConfidenceFeatureProvider(Confidence)} instead.
   */
  @Deprecated()
  public ConfidenceFeatureProvider(String clientSecret, String host, int port) {
    this(clientSecret, ManagedChannelBuilder.forAddress(host, port).usePlaintext().build());
  }

  @Override
  public Metadata getMetadata() {
    return () -> "com.spotify.confidence.flags.resolver.v1.FlagResolverService";
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
  public ProviderEvaluation<Value> getObjectEvaluation(
      String key, Value defaultValue, EvaluationContext ctx) {

    final FlagPath flagPath;
    try {
      flagPath = FlagPath.getPath(key);
    } catch (IllegalValuePath e) {
      log.warn(e.getMessage());
      throw new RuntimeException(e);
    }

    final Struct evaluationContext = OpenFeatureUtils.convertToProto(ctx);
    // resolve the flag by calling the resolver API
    final ResolveFlagsResponse resolveFlagResponse;
    try {
      final String requestFlagName = "flags/" + flagPath.getFlag();

      resolveFlagResponse =
          confidence
              .withContext(
                  Map.of(
                      OPEN_FEATURE_RESOLVE_CONTEXT_KEY,
                      ConfidenceValue.Struct.fromProto(evaluationContext)))
              .resolveFlags(requestFlagName)
              .get();

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
            .variant(resolvedFlag.getVariant())
            .build();
      }
    } catch (StatusRuntimeException | InterruptedException | ExecutionException e) {
      // If the remote API is unreachable, for now we fall back to the default value. However, we
      // should consider maintaining a local resolve-history to avoid flickering experience in case
      // of a temporarily unavailable backend
      if (e instanceof StatusRuntimeException) {
        handleStatusRuntimeException((StatusRuntimeException) e);
      } else if (e.getCause() instanceof StatusRuntimeException) {
        handleStatusRuntimeException((StatusRuntimeException) e.getCause());
      }
      throw new GeneralError("Unknown error occurred when calling the provider backend");
    }
  }

  private static void handleStatusRuntimeException(StatusRuntimeException e) {
    if (e.getStatus().getCode() == Code.DEADLINE_EXCEEDED) {
      log.error("Deadline exceeded when calling provider backend", e);
      throw new GeneralError("Deadline exceeded when calling provider backend");
    } else if (e.getStatus().getCode() == Code.UNAVAILABLE) {
      log.error("Provider backend is unavailable", e);
      throw new GeneralError("Provider backend is unavailable");
    } else if (e.getStatus().getCode() == Code.UNAUTHENTICATED) {
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
