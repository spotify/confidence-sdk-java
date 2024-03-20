package com.spotify.confidence;

import com.google.protobuf.Struct;
import com.spotify.confidence.shaded.flags.resolver.v1.ResolveFlagsResponse;
import com.spotify.confidence.shaded.flags.resolver.v1.ResolvedFlag;
import dev.openfeature.sdk.EvaluationContext;
import dev.openfeature.sdk.FeatureProvider;
import dev.openfeature.sdk.Metadata;
import dev.openfeature.sdk.ProviderEvaluation;
import dev.openfeature.sdk.Structure;
import dev.openfeature.sdk.Value;
import dev.openfeature.sdk.exceptions.FlagNotFoundError;
import dev.openfeature.sdk.exceptions.GeneralError;
import dev.openfeature.sdk.exceptions.TypeMismatchError;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Status.Code;
import io.grpc.StatusRuntimeException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import java.util.regex.Pattern;

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

    final FlagPath flagPath = getPath(key);

    final Struct evaluationContext = OpenFeatureUtils.convertToProto(ctx);
    // resolve the flag by calling the resolver API
    final ResolveFlagsResponse resolveFlagResponse;
    try {
      final String requestFlagName = "flags/" + flagPath.getFlag();

      resolveFlagResponse =
          confidence
              .withContext(ConfidenceValue.Struct.fromProto(evaluationContext))
              .resolveFlags(requestFlagName)
              .get();

      if (resolveFlagResponse.getResolvedFlagsList().isEmpty()) {
        throw new FlagNotFoundError(
            String.format("No active flag '%s' was found", flagPath.getFlag()));
      }

      final String responseFlagName = resolveFlagResponse.getResolvedFlags(0).getFlag();
      if (!requestFlagName.equals(responseFlagName)) {
        throw new FlagNotFoundError(
            String.format(
                "Unexpected flag '%s' from remote", responseFlagName.replaceFirst("^flags/", "")));
      }

      final ResolvedFlag resolvedFlag = resolveFlagResponse.getResolvedFlags(0);

      if (resolvedFlag.getVariant().isEmpty()) {
        return ProviderEvaluation.<Value>builder()
            .value(defaultValue)
            .reason(
                "The server returned no assignment for the flag. Typically, this happens "
                    + "if no configured rules matches the given evaluation context.")
            .build();
      } else {
        final Value fullValue =
            TypeMapper.from(resolvedFlag.getValue(), resolvedFlag.getFlagSchema());

        // if a path is given, extract expected portion from the structured value
        Value value = getValueForPath(flagPath.getPath(), fullValue);

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
      throw new GeneralError("Deadline exceeded when calling provider backend");
    } else if (e.getStatus().getCode() == Code.UNAVAILABLE) {
      throw new GeneralError("Provider backend is unavailable");
    } else if (e.getStatus().getCode() == Code.UNAUTHENTICATED) {
      throw new GeneralError("UNAUTHENTICATED");
    } else {
      throw new GeneralError(
          String.format(
              "Unknown error occurred when calling the provider backend. Exception: %s",
              e.getMessage()));
    }
  }

  private static Value getValueForPath(List<String> path, Value fullValue) {
    Value value = fullValue;
    for (String fieldName : path) {
      final Structure structure = value.asStructure();
      if (structure == null) {
        // value's inner object actually is no structure
        throw new TypeMismatchError(
            String.format(
                "Illegal attempt to derive field '%s' on non-structure value '%s'",
                fieldName, value));
      }

      value = structure.getValue(fieldName);

      if (value == null) {
        // we know that null indicates absence of a proper value because intended nulls would be an
        // instance of type Value
        throw new TypeMismatchError(
            String.format(
                "Illegal attempt to derive non-existing field '%s' on structure value '%s'",
                fieldName, structure));
      }
    }

    return value;
  }

  private static FlagPath getPath(String str) {
    final String regex = Pattern.quote(".");
    final String[] parts = str.split(regex);

    if (parts.length == 0) {
      // this happens for malformed corner cases such as: str = "..."
      throw new GeneralError(String.format("Illegal path string '%s'", str));
    } else if (parts.length == 1) {
      // str doesn't contain the delimiter
      return new FlagPath(str, List.of());
    } else {
      return new FlagPath(parts[0], Arrays.asList(parts).subList(1, parts.length));
    }
  }

  private static class FlagPath {

    private final String flag;
    private final List<String> path;

    public FlagPath(String flag, List<String> path) {
      this.flag = flag;
      this.path = path;
    }

    public String getFlag() {
      return flag;
    }

    public List<String> getPath() {
      return path;
    }
  }
}
