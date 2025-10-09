package com.spotify.confidence;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nonnull;

public non-sealed interface MaterializationRepository extends StickyResolveStrategy {
  @Nonnull
  CompletableFuture<Map<String, MaterializationInfo>> loadMaterializedAssignmentsForUnit(
      @Nonnull String unit, @Nonnull String materialization);

  @Nonnull
  CompletableFuture<Void> storeAssignment(
      @Nonnull String unit, Map<String, MaterializationInfo> assignments);
}
