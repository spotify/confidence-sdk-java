package com.spotify.confidence.targeting.value;

import static com.google.protobuf.util.Timestamps.toNanos;

import com.spotify.confidence.shaded.flags.types.v1.Targeting;

public interface Ord {

  boolean lt(Targeting.Value a, Targeting.Value b);

  boolean lte(Targeting.Value a, Targeting.Value b);

  static Ord getOrd(Targeting.Value value) {
    switch (value.getValueCase()) {
      case NUMBER_VALUE:
        return new Ord() {
          @Override
          public boolean lt(Targeting.Value a, Targeting.Value b) {
            return a.getNumberValue() < b.getNumberValue();
          }

          @Override
          public boolean lte(Targeting.Value a, Targeting.Value b) {
            return a.getNumberValue() <= b.getNumberValue();
          }
        };
      case TIMESTAMP_VALUE:
        return new Ord() {
          @Override
          public boolean lt(Targeting.Value a, Targeting.Value b) {
            return toNanos(a.getTimestampValue()) < toNanos(b.getTimestampValue());
          }

          @Override
          public boolean lte(Targeting.Value a, Targeting.Value b) {
            return toNanos(a.getTimestampValue()) <= toNanos(b.getTimestampValue());
          }
        };
      case VERSION_VALUE:
        return new Ord() {
          @Override
          public boolean lt(Targeting.Value a, Targeting.Value b) {
            final SemanticVersion versionA =
                SemanticVersion.fromVersionString(a.getVersionValue().getVersion());
            final SemanticVersion versionB =
                SemanticVersion.fromVersionString(b.getVersionValue().getVersion());
            return versionA.compareTo(versionB) < 0;
          }

          @Override
          public boolean lte(Targeting.Value a, Targeting.Value b) {
            final SemanticVersion versionA =
                SemanticVersion.fromVersionString(a.getVersionValue().getVersion());
            final SemanticVersion versionB =
                SemanticVersion.fromVersionString(b.getVersionValue().getVersion());
            return versionA.compareTo(versionB) <= 0;
          }
        };

      case BOOL_VALUE:
      case STRING_VALUE:
      default:
        throw new UnsupportedOperationException(value.getValueCase() + " is not comparable");
    }
  }
}
