package com.spotify.confidence.flags.resolver;

import com.google.common.base.Throwables;
import com.spotify.confidence.flags.admin.v1.Flag;
import com.spotify.confidence.flags.resolver.materialization.MaterializationInfo;
import com.spotify.confidence.flags.resolver.materialization.MaterializationStore;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class MaterializationsHelper {
  private static final Logger logger = LoggerFactory.getLogger(MaterializationsHelper.class);
  private final Map<String, List<String>> materializationsToRead;
  private final Map<String, CompletableFuture<Map<String, MaterializationInfo>>>
      unitToAssignmentsLoader = new HashMap<>();

  private final Map<String, Map<String, MaterializationInfo>> materializationsToWritePerUnit =
      new ConcurrentHashMap<>();

  private final MaterializationStore materializationStore;
  private final Metrics metrics;

  MaterializationsHelper(
      final List<Flag> flagsToResolve,
      final MaterializationStore materializationStore,
      Metrics metrics) {
    this.materializationsToRead =
        flagsToResolve.stream()
            .flatMap(c -> c.getRulesList().stream())
            .filter(c -> !c.getMaterializationSpec().getReadMaterialization().isEmpty())
            .collect(
                Collectors.groupingBy(
                    c -> c.getMaterializationSpec().getReadMaterialization(),
                    Collectors.mapping(Flag.Rule::getName, Collectors.toList())));
    this.materializationStore = materializationStore;
    this.metrics = metrics;
  }

  CompletableFuture<MaterializationInfo> loadMaterializationInfoForUnit(
      String unit, String materialization) {
    return unitToAssignmentsLoader
        .computeIfAbsent(
            unit,
            ignore ->
                materializationStore.loadMaterializedAssignmentsForUnit(
                    unit, materializationsToRead))
        .thenApply(
            c ->
                c.containsKey(materialization)
                    ? c.get(materialization)
                    : MaterializationInfo.empty())
        .exceptionally(
            ex -> {
              final var rootCause = Throwables.getRootCause(ex);
              metrics.failedMaterializationLoad(rootCause);
              return MaterializationInfo.empty();
            });
  }

  void addMaterializationToWrite(
      String unit, String materialization, String rule, String variantOrEmpty) {
    final MaterializationInfo materializationInfo =
        materializationsToWritePerUnit
            .computeIfAbsent(unit, i -> new ConcurrentHashMap<>())
            .computeIfAbsent(
                materialization, i -> new MaterializationInfo(true, new ConcurrentHashMap<>()));
    if (!variantOrEmpty.isEmpty()) {
      materializationInfo.ruleToVariant().put(rule, variantOrEmpty);
    }
  }

  public Map<String, Map<String, MaterializationInfo>> materializationsToWritePerUnit() {
    return materializationsToWritePerUnit;
  }
}
