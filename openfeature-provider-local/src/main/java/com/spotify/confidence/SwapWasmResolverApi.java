package com.spotify.confidence;

import com.spotify.confidence.flags.resolver.v1.MaterializationMap;
import com.spotify.confidence.flags.resolver.v1.ResolveWithStickyRequest;
import com.spotify.confidence.flags.resolver.v1.ResolveWithStickyResponse;
import com.spotify.confidence.shaded.flags.resolver.v1.ResolveFlagsRequest;
import com.spotify.confidence.shaded.flags.resolver.v1.ResolveFlagsResponse;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

class SwapWasmResolverApi implements ResolverApi {
  private final AtomicReference<WasmResolveApi> wasmResolverApiRef = new AtomicReference<>();
  private final StickyResolveStrategy stickyResolveStrategy;
  private final WasmFlagLogger flagLogger;
  private final RetryStrategy retryStrategy;

  public SwapWasmResolverApi(
      WasmFlagLogger flagLogger,
      byte[] initialState,
      String accountId,
      StickyResolveStrategy stickyResolveStrategy,
      RetryStrategy retryStrategy) {
    this.stickyResolveStrategy = stickyResolveStrategy;
    this.flagLogger = flagLogger;
    this.retryStrategy = retryStrategy;

    // Create initial instance
    final WasmResolveApi initialInstance = new WasmResolveApi(flagLogger, retryStrategy);
    initialInstance.setResolverState(initialState, accountId);
    this.wasmResolverApiRef.set(initialInstance);
  }

  @Override
  public void updateStateAndFlushLogs(byte[] state, String accountId) {
    logResolveLock.lock();
    // Create new instance with updated state
    final WasmResolveApi newInstance = new WasmResolveApi(flagLogger, retryStrategy);
    newInstance.setResolverState(state, accountId);

    // Get current instance before switching
    final WasmResolveApi oldInstance = wasmResolverApiRef.getAndSet(newInstance);

    if (oldInstance != null) {
      oldInstance.flushLogs();
      oldInstance.isIsFlushed(true);
    }
    logResolveLock.unlock();
  }

  @Override
  public void close() {
    final WasmResolveApi currentInstance = wasmResolverApiRef.get();
    if (currentInstance != null) {
      // Note: WasmResolveApi doesn't have a close method, so this is a no-op for now
      // but the instance will be GC'd naturally
    }
  }

  private final ReentrantLock logResolveLock = new ReentrantLock();

  @Override
  public CompletableFuture<ResolveFlagsResponse> resolveWithSticky(
      ResolveWithStickyRequest request) {
    logResolveLock.lock();
    final var response = resolveWithStickyInternal(request);
    logResolveLock.unlock();
    return response;
  }

  private CompletableFuture<ResolveFlagsResponse> resolveWithStickyInternal(
      ResolveWithStickyRequest request) {
    final var instance = wasmResolverApiRef.get();
    final var response = instance.resolveWithSticky(request);

    switch (response.getResolveResultCase()) {
      case SUCCESS -> {
        final var success = response.getSuccess();
        // Store updates if present
        if (!success.getUpdatesList().isEmpty()) {
          storeUpdates(success.getUpdatesList());
        }
        if (instance.isFlushed()) {
          CompletableFuture.runAsync(instance::flushLogs);
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
  public ResolveFlagsResponse resolve(ResolveFlagsRequest request) {
    logResolveLock.lock();
    final var instance = wasmResolverApiRef.get();
    final var response = instance.resolve(request);
    if (instance.isFlushed()) {
      CompletableFuture.runAsync(instance::flushLogs);
    }
    logResolveLock.unlock();
    return response;
  }
}
