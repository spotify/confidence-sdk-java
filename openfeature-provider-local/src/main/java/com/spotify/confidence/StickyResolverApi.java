package com.spotify.confidence;

import com.spotify.confidence.flags.resolver.v1.MaterializationMap;
import com.spotify.confidence.flags.resolver.v1.ResolveWithStickyRequest;
import com.spotify.confidence.flags.resolver.v1.ResolveWithStickyResponse;
import com.spotify.confidence.shaded.flags.resolver.v1.ResolveFlagsResponse;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

class StickyResolverApi implements ResolverApi {
  private final WasmResolveApi wasmResolverApi;
  private final StickyResolveStrategy stickyResolveStrategy;

  public StickyResolverApi(
      WasmResolveApi wasmResolverApi, StickyResolveStrategy stickyResolveStrategy) {
    this.wasmResolverApi = wasmResolverApi;
    this.stickyResolveStrategy = stickyResolveStrategy;
  }

  @Override
  public CompletableFuture<ResolveFlagsResponse> resolve(ResolveWithStickyRequest request) {
    final ResolveWithStickyResponse response;
    response = wasmResolverApi.resolve(request);

    switch (response.getResolveResultCase()) {
      case SUCCESS -> {
        final var success = response.getSuccess();
        // Store updates if present
        if (!success.getUpdatesList().isEmpty()) {
          storeUpdates(success.getUpdatesList());
        }
        return CompletableFuture.completedFuture(success.getResponse());
      }
      case MISSING_MATERIALIZATIONS -> {
        final var missingMaterializations = response.getMissingMaterializations();

        // Check for ResolverFallback first - return early if so
        if (stickyResolveStrategy instanceof ResolverFallback fallback) {
          return fallback.resolve(request.getResolveRequest());
        }

        // Handle MaterializationRepository case
        if (stickyResolveStrategy instanceof MaterializationRepository repository) {
          final var currentRequest =
              handleMissingMaterializations(
                  request, missingMaterializations.getItemsList(), repository);
          return resolve(currentRequest);
        }

        throw new RuntimeException(
            "Unknown sticky resolve strategy: " + stickyResolveStrategy.getClass());
      }
      case RESOLVERESULT_NOT_SET ->
          throw new RuntimeException("Invalid response: resolve result not set");
      default ->
          throw new RuntimeException("Unhandled response case: " + response.getResolveResultCase());
    }
  }

  private void storeUpdates(List<ResolveWithStickyResponse.MaterializationUpdate> updates) {
    if (stickyResolveStrategy instanceof MaterializationRepository repository) {
      CompletableFuture.runAsync(
          () -> {
            // Group updates by unit
            final var updatesByUnit =
                updates.stream()
                    .collect(
                        Collectors.groupingBy(
                            ResolveWithStickyResponse.MaterializationUpdate::getUnit));

            // Store assignments for each unit
            updatesByUnit.forEach(
                (unit, unitUpdates) -> {
                  final Map<String, MaterializationInfo> assignments = new HashMap<>();
                  unitUpdates.forEach(
                      update -> {
                        final var ruleToVariant = Map.of(update.getRule(), update.getVariant());
                        assignments.put(
                            update.getWriteMaterialization(),
                            new MaterializationInfo(true, ruleToVariant));
                      });

                  repository
                      .storeAssignment(unit, assignments)
                      .exceptionally(
                          throwable -> {
                            // Log error but don't propagate to avoid affecting main resolve path
                            System.err.println(
                                "Failed to store materialization updates for unit "
                                    + unit
                                    + ": "
                                    + throwable.getMessage());
                            return null;
                          });
                });
          });
    }
  }

  private ResolveWithStickyRequest handleMissingMaterializations(
      ResolveWithStickyRequest request,
      List<ResolveWithStickyResponse.MissingMaterializationItem> missingItems,
      MaterializationRepository repository) {

    // Group missing items by unit for efficient loading
    final var missingByUnit =
        missingItems.stream()
            .collect(
                Collectors.groupingBy(
                    ResolveWithStickyResponse.MissingMaterializationItem::getUnit));

    final HashMap<String, MaterializationMap> materializationPerUnitMap = new HashMap<>();

    // Load materialized assignments for all missing units
    missingByUnit.forEach(
        (unit, materializationInfoItem) -> {
          materializationInfoItem.forEach(
              item -> {
                final Map<String, MaterializationInfo> loadedAssignments;
                try {
                  loadedAssignments =
                      repository
                          .loadMaterializedAssignmentsForUnit(unit, item.getReadMaterialization())
                          .get();
                } catch (InterruptedException | ExecutionException e) {
                  throw new RuntimeException(e);
                }
                materializationPerUnitMap.computeIfAbsent(
                    unit,
                    k -> MaterializationMap.newBuilder().putAllInfoMap(new HashMap<>()).build());
                materializationPerUnitMap.computeIfPresent(
                    unit,
                    (k, v) -> {
                      final Map<
                              String, com.spotify.confidence.flags.resolver.v1.MaterializationInfo>
                          map = new HashMap<>();
                      loadedAssignments.forEach(
                          (s, materializationInfo) -> {
                            map.put(s, materializationInfo.toProto());
                          });
                      return v.toBuilder().putAllInfoMap(map).build();
                    });
              });
        });

    // Return new request with updated materialization context
    return request.toBuilder().putAllMaterializationsPerUnit(materializationPerUnitMap).build();
  }

  @Override
  public void flush() {
    wasmResolverApi.flush();
  }
}
