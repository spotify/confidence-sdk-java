package com.spotify.confidence;

import com.spotify.confidence.ConfidenceValue.Struct;
import dev.openfeature.sdk.Structure;
import dev.openfeature.sdk.Value;
import dev.openfeature.sdk.exceptions.GeneralError;
import dev.openfeature.sdk.exceptions.TypeMismatchError;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.regex.Pattern;
import org.slf4j.Logger;

final class SdkUtils {

  private SdkUtils() {}

  private static final Logger log = org.slf4j.LoggerFactory.getLogger(SdkUtils.class);

  static String getSdkVersion() {
    try {
      final Properties prop = new Properties();
      prop.load(SdkUtils.class.getResourceAsStream("/version.properties"));
      return prop.getProperty("version");
    } catch (IOException e) {
      throw new RuntimeException("Can't determine version of the SDK", e);
    }
  }

  static FlagPath getPath(String str) {
    final String regex = Pattern.quote(".");
    final String[] parts = str.split(regex);

    if (parts.length == 0) {
      // this happens for malformed corner cases such as: str = "..."
      log.warn("Illegal path string '{}'", str);
      throw new GeneralError(String.format("Illegal path string '%s'", str));
    } else if (parts.length == 1) {
      // str doesn't contain the delimiter
      return new FlagPath(str, List.of());
    } else {
      return new FlagPath(parts[0], Arrays.asList(parts).subList(1, parts.length));
    }
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
  }

  static Value getValueForPath(List<String> path, Value fullValue) {
    Value value = fullValue;
    for (String fieldName : path) {
      final Structure structure = value.asStructure();
      if (structure == null) {
        // value's inner object actually is no structure
        log.warn(
            "Illegal attempt to derive field '{}' on non-structure value '{}'", fieldName, value);
        throw new TypeMismatchError(
            String.format(
                "Illegal attempt to derive field '%s' on non-structure value '%s'",
                fieldName, value));
      }

      value = structure.getValue(fieldName);

      if (value == null) {
        // we know that null indicates absence of a proper value because intended nulls would be an
        // instance of type Value
        log.warn(
            "Illegal attempt to derive non-existing field '{}' on structure value '{}'",
            fieldName,
            structure);
        throw new TypeMismatchError(
            String.format(
                "Illegal attempt to derive non-existing field '%s' on structure value '%s'",
                fieldName, structure));
      }
    }

    return value;
  }

  static ConfidenceValue getValueForPath(List<String> path, ConfidenceValue fullValue) {
    ConfidenceValue value = fullValue;
    for (String fieldName : path) {
      final Struct structure = value.asStruct();
      if (structure == null) {
        // value's inner object actually is no structure
        log.warn(
            "Illegal attempt to derive field '{}' on non-structure value '{}'", fieldName, value);
        throw new TypeMismatchError(
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
        throw new TypeMismatchError(
            String.format(
                "Illegal attempt to derive non-existing field '%s' on structure value '%s'",
                fieldName, structure));
      }
    }

    return value;
  }
}
