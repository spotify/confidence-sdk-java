package com.spotify.confidence;

import com.spotify.confidence.shaded.flags.types.v1.Targeting;

class Targetings {

  static Targeting.Value boolValue(final boolean value) {
    return Targeting.Value.newBuilder().setBoolValue(value).build();
  }

  static Targeting.Value numberValue(final double value) {
    return Targeting.Value.newBuilder().setNumberValue(value).build();
  }

  static Targeting.Value stringValue(final String value) {
    return Targeting.Value.newBuilder().setStringValue(value).build();
  }

  static Targeting.Value semverValue(final String value) {
    return Targeting.Value.newBuilder()
        .setVersionValue(Targeting.SemanticVersion.newBuilder().setVersion(value).build())
        .build();
  }
}
