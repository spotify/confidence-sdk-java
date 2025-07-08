package com.spotify.confidence;

import com.spotify.confidence.ConfidenceValue.Struct;
import com.spotify.confidence.Exceptions.IllegalValuePath;
import com.spotify.confidence.Exceptions.ValueNotFound;
import io.grpc.StatusRuntimeException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.function.Function;
import java.util.regex.Pattern;
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

  static class FlagPath {
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

    public static FlagPath getPath(String str) throws IllegalValuePath {
      final String regex = Pattern.quote(".");
      final String[] parts = str.split(regex);

      if (parts.length == 0) {
        // this happens for malformed corner cases such as: str = "..."
        log.warn("Illegal path string '{}'", str);
        throw new IllegalValuePath(String.format("Illegal path string '%s'", str));
      } else if (parts.length == 1) {
        // str doesn't contain the delimiter
        return new FlagPath(str, List.of());
      } else {
        return new FlagPath(parts[0], Arrays.asList(parts).subList(1, parts.length));
      }
    }
  }

  static String getSdkVersion() {
    return "0.1.10"; // x-release-please-version
  }
}
