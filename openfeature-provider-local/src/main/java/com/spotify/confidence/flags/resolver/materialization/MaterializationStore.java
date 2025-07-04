package com.spotify.confidence.flags.resolver.materialization;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public interface MaterializationStore {
  CompletableFuture<Map<String, MaterializationInfo>> loadMaterializedAssignmentsForUnit(
      String unit, Map<String, List<String>> materializationsToRules);

  CompletableFuture<Void> storeAssignment(
      String unit, Map<String, MaterializationInfo> assignments);
}
