package com.spotify.confidence;

import com.spotify.confidence.ConfidenceValue.Struct;
import com.spotify.confidence.Exceptions.IllegalValuePath;
import com.spotify.confidence.Exceptions.ValueNotFound;
import io.grpc.StatusRuntimeException;
import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.function.Function;
import org.slf4j.Logger;

// Be careful if you intend moving this file. The version returned by `getSdkVersion()` is set by
// Release Please by defining its path as an "extra file" in release-please-config.json
final class ConfidenceUtils {

  private ConfidenceUtils() {}

  private static final Logger log = org.slf4j.LoggerFactory.getLogger(ConfidenceUtils.class);

  static ConfidenceValue getValueForPath(List<String> path, ConfidenceValue fullValue)
      throws ValueNotFound {
    ConfidenceValue value = fullValue;
    for (String fieldName : path) {
      final Struct structure = value.asStruct();
      if (structure == null) {
        // value's inner object actually is no structure
        log.warn(
            "Illegal attempt to derive field '{}' on non-structure value '{}'", fieldName, value);
        throw new ValueNotFound(
            String.format(
                "Illegal attempt to derive field '%s' on non-structure value '%s'",
                fieldName, value));
      }

      value = structure.get(fieldName);

      if (value == null) {
        // we know that null indicates absence of a proper value because intended nulls would be an
        // instance of type Value
        log.warn(
            "Illegal attempt to derive non-existing field '{}' on structure value '{}'",
            fieldName,
            structure);
        throw new ValueNotFound(
            String.format(
                "Illegal attempt to derive non-existing field '%s' on structure value '%s'",
                fieldName, structure));
      }
    }

    return value;
  }

  public static <T> Function<Throwable, ? extends FlagEvaluation<T>> handleFlagEvaluationError(
      T defaultValue) {
    return (Function<Throwable, FlagEvaluation<T>>)
        e -> {
          {
            if (e instanceof CompletionException) {
              e = e.getCause();
            }
            log.warn(e.getMessage());
            if (e instanceof IllegalValuePath || e instanceof ValueNotFound) {
              return new FlagEvaluation<>(
                  defaultValue, "", "ERROR", ErrorType.INVALID_VALUE_PATH, e.getMessage());
            } else if (e instanceof Exceptions.IncompatibleValueType
                || e instanceof Exceptions.IllegalValueType) {
              return new FlagEvaluation<>(
                  defaultValue, "", "ERROR", ErrorType.INVALID_VALUE_TYPE, e.getMessage());
            } else if (e instanceof StatusRuntimeException
                || e.getCause() instanceof StatusRuntimeException) {
              return new FlagEvaluation<>(
                  defaultValue, "", "ERROR", ErrorType.NETWORK_ERROR, e.getMessage());
            } else {
              return new FlagEvaluation<>(
                  defaultValue, "", "ERROR", ErrorType.INTERNAL_ERROR, e.getMessage());
            }
          }
        };
  }

  static String getSdkVersion() {
    return "0.2.2"; // x-release-please-version
  }
}
