package com.spotify.confidence;

import com.spotify.confidence.shaded.flags.resolver.v1.ResolveFlagsRequest;
import com.spotify.confidence.shaded.flags.resolver.v1.ResolveFlagsResponse;
import java.util.concurrent.atomic.AtomicReference;

class SwapWasmResolverApi {
  private final AtomicReference<WasmResolveApi> wasmResolverApiRef = new AtomicReference<>();
  private final WasmResolveApi primaryWasmResolverApi;
  private final WasmResolveApi secondaryWasmResolverApi;
  private Boolean isPrimary = true;

  public SwapWasmResolverApi(FlagLogger flagLogger, byte[] initialState) {
    this.primaryWasmResolverApi = new WasmResolveApi(flagLogger);
    this.primaryWasmResolverApi.setResolverState(initialState);
    this.secondaryWasmResolverApi = new WasmResolveApi(flagLogger);
    this.secondaryWasmResolverApi.setResolverState(initialState);
    this.wasmResolverApiRef.set(primaryWasmResolverApi);
  }

  public void updateState(byte[] state) {
    if (isPrimary) {
      this.secondaryWasmResolverApi.setResolverState(state);
      this.wasmResolverApiRef.set(secondaryWasmResolverApi);
    } else {
      this.primaryWasmResolverApi.setResolverState(state);
      this.wasmResolverApiRef.set(primaryWasmResolverApi);
    }
    isPrimary = !isPrimary;
  }

  public ResolveFlagsResponse resolve(ResolveFlagsRequest request) {
    return wasmResolverApiRef.get().resolve(request);
  }
}
