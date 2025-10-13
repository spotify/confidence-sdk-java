package com.spotify.confidence.expressions;

import com.spotify.confidence.shaded.flags.types.v1.Targeting;
import java.math.BigDecimal;
import java.util.List;
import java.util.OptionalLong;

public interface Eq {

  double EPSILON = 0.00000001d;

  boolean eq(Targeting.Value a, Targeting.Value b);

  default boolean contains(List<Targeting.Value> a, Targeting.Value b) {
    return a.stream().anyMatch(v -> eq(v, b));
  }

  default boolean overlap(List<Targeting.Value> a, List<Targeting.Value> b) {
    return a.stream().anyMatch(v -> contains(b, v));
  }

  default boolean overlapNeg(List<Targeting.Value> a, List<Targeting.Value> bNeg) {
    return a.stream().anyMatch(v -> !contains(bNeg, v));
  }

  /**
   * not sure if we need this, we could just compare the proto value itself, it already has defined
   * equality
   */
  static Eq getEq(Targeting.Value.ValueCase valueCase) {
    switch (valueCase) {
      case BOOL_VALUE:
        return (a, b) -> a.getBoolValue() == b.getBoolValue();
      case NUMBER_VALUE:
        return (a, b) -> {
          final double aValue = a.getNumberValue();
          final double bValue = b.getNumberValue();
          final OptionalLong aLong = tryGetLong(aValue);
          final OptionalLong bLong = tryGetLong(bValue);

          if (aLong.isPresent() && bLong.isPresent()) {
            return aLong.getAsLong() == bLong.getAsLong();
          } else {
            return Math.abs(aValue - bValue) < EPSILON;
          }
        };
      case STRING_VALUE:
        return (a, b) -> a.getStringValue().equals(b.getStringValue());
      case TIMESTAMP_VALUE:
        return (a, b) -> a.getTimestampValue().equals(b.getTimestampValue());
      case VERSION_VALUE:
        return (a, b) -> a.getVersionValue().getVersion().equals(b.getVersionValue().getVersion());
      default:
        throw new UnsupportedOperationException();
    }
  }

  private static OptionalLong tryGetLong(double value) {
    try {
      return OptionalLong.of(BigDecimal.valueOf(value).longValueExact());
    } catch (ArithmeticException e) {
      return OptionalLong.empty();
    }
  }
}
