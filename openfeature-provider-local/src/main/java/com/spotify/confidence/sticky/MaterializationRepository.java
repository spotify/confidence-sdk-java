package com.spotify.confidence.sticky;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public non-sealed interface MaterializationRepository extends StickyResolveStrategy {
  CompletableFuture<Map<String, MaterializationInfo>> loadMaterializedAssignmentsForUnit(
      String unit, Map<String, List<String>> materializationsToRules);

  CompletableFuture<Void> storeAssignment(
      String unit, Map<String, MaterializationInfo> assignments);
}
