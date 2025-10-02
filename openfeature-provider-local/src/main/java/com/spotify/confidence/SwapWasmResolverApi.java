package com.spotify.confidence;

import com.spotify.confidence.flags.resolver.v1.ResolveWithStickyRequest;
import com.spotify.confidence.flags.resolver.v1.ResolveWithStickyResponse;
import com.spotify.confidence.shaded.flags.resolver.v1.ResolveFlagsRequest;
import com.spotify.confidence.shaded.flags.resolver.v1.ResolveFlagsResponse;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

class SwapWasmResolverApi {
  private final AtomicReference<WasmResolveApi> wasmResolverApiRef = new AtomicReference<>();
  private final WasmResolveApi primaryWasmResolverApi;
  private final WasmResolveApi secondaryWasmResolverApi;
  private final StickyResolveStrategy stickyResolveStrategy;
  private Boolean isPrimary = true;

  public SwapWasmResolverApi(
      WasmFlagLogger flagLogger,
      byte[] initialState,
      String accountId,
      StickyResolveStrategy stickyResolveStrategy) {
    this.stickyResolveStrategy = stickyResolveStrategy;
    this.primaryWasmResolverApi = new WasmResolveApi(flagLogger);
    this.primaryWasmResolverApi.setResolverState(initialState, accountId);
    this.secondaryWasmResolverApi = new WasmResolveApi(flagLogger);
    this.secondaryWasmResolverApi.setResolverState(initialState, accountId);
    this.wasmResolverApiRef.set(primaryWasmResolverApi);
  }

  public void updateState(byte[] state, String accountId) {
    if (isPrimary) {
      this.secondaryWasmResolverApi.setResolverState(state, accountId);
      this.wasmResolverApiRef.set(secondaryWasmResolverApi);
    } else {
      this.primaryWasmResolverApi.setResolverState(state, accountId);
      this.wasmResolverApiRef.set(primaryWasmResolverApi);
    }
    isPrimary = !isPrimary;
  }

  public void close() {
    primaryWasmResolverApi.close();
    secondaryWasmResolverApi.close();
  }

  private final ReentrantLock logResolveLock = new ReentrantLock();

  public CompletableFuture<ResolveFlagsResponse> resolveWithSticky(
      ResolveWithStickyRequest request) {
    logResolveLock.lock();
    final var response = resolveWithStickyInternal(request);
    logResolveLock.unlock();
    return response;
  }

  private CompletableFuture<ResolveFlagsResponse> resolveWithStickyInternal(
      ResolveWithStickyRequest request) {
    final var response = wasmResolverApiRef.get().resolveWithSticky(request);

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
          return resolveWithStickyInternal(currentRequest);
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
                    ResolveWithStickyResponse.MissingMaterializationItem::getUnit,
                    Collectors.toMap(
                        ResolveWithStickyResponse.MissingMaterializationItem
                            ::getReadMaterialization,
                        item -> List.of(item.getRule()),
                        (existing, replacement) -> {
                          final var combined = new java.util.ArrayList<>(existing);
                          combined.addAll(replacement);
                          return combined;
                        })));

    // Load materialized assignments for all missing units
    final var materializationContext = request.getMaterializationContext().toBuilder();

    missingByUnit.forEach(
        (unit, materializationsToRules) -> {
          try {
            final var loadedAssignments =
                repository.loadMaterializedAssignmentsForUnit(unit, materializationsToRules).get();
            loadedAssignments.forEach(
                (materialization, info) ->
                    materializationContext.putUnitMaterializationInfo(unit, info.toProto()));
          } catch (Exception e) {
            throw new RuntimeException(
                "Failed to load materialized assignments for unit: " + unit, e);
          }
        });

    // Return new request with updated materialization context
    return request.toBuilder().setMaterializationContext(materializationContext.build()).build();
  }

  public ResolveFlagsResponse resolve(ResolveFlagsRequest request) {
    logResolveLock.lock();
    final var response = wasmResolverApiRef.get().resolve(request);
    logResolveLock.unlock();
    return response;
  }
}
