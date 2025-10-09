package com.spotify.confidence;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.Optional;

public record MaterializationInfo(
    boolean isUnitInMaterialization, @Nonnull Map<String, String> ruleToVariant) {
  public Optional<String> getVariantForRule(String rule) {
    return Optional.ofNullable(ruleToVariant.get(rule));
  }

  public static MaterializationInfo empty() {
    return new MaterializationInfo(false, Map.of());
  }

  public com.spotify.confidence.flags.resolver.v1.MaterializationInfo toProto() {
    return com.spotify.confidence.flags.resolver.v1.MaterializationInfo.newBuilder()
        .setUnitInInfo(this.isUnitInMaterialization)
        .putAllRuleToVariant(this.ruleToVariant)
        .build();
  }
}
