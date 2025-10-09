package com.spotify.confidence;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public non-sealed interface MaterializationRepository extends StickyResolveStrategy {
  @Nonnull
  CompletableFuture<@Nonnull Map<String, MaterializationInfo>> loadMaterializedAssignmentsForUnit(
      @Nonnull String unit, @Nonnull String materialization);

  @Nonnull
  CompletableFuture<Void> storeAssignment(
      @Nonnull String unit, @Nonnull Map<String, MaterializationInfo> assignments);
}
