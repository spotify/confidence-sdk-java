package com.spotify.confidence;

import java.util.Map;
import java.util.Optional;

public record MaterializationInfo(
    boolean isUnitInMaterialization, Map<String, String> ruleToVariant) {
  public Optional<String> getVariantForRule(String rule) {
    return Optional.ofNullable(ruleToVariant.get(rule));
  }

  public static MaterializationInfo empty() {
    return new MaterializationInfo(false, Map.of());
  }
}
