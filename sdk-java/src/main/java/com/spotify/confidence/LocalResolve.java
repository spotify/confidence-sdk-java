package com.spotify.confidence;

import com.dylibso.chicory.wasm.Parser;
import com.dylibso.chicory.wasm.WasmModule;
import com.google.protobuf.Struct;
import com.spotify.confidence.shaded.flags.resolver.v1.ResolveFlagsRequest;
import com.spotify.confidence.shaded.flags.resolver.v1.ResolveFlagsResponse;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.CompletableFuture;

public class LocalResolve implements ResolveMode {
  private final LocalResolverApi localResolverApi;
  private final String clientSecret;

  @Override
  public void close() {}

  public LocalResolve(String clientSecret) {
    this.clientSecret = clientSecret;

    // Load WASM module from resources
    try (InputStream wasmStream =
        getClass().getClassLoader().getResourceAsStream("wasm/rust_guest.wasm")) {
      if (wasmStream == null) {
        throw new RuntimeException("Could not find rust_guest.wasm in resources");
      }
      WasmModule module = Parser.parse(wasmStream);
      this.localResolverApi = new LocalResolverApi(module);
      setResolverState();
    } catch (IOException e) {
      throw new RuntimeException("Failed to load WASM module", e);
    }
  }

  private void setResolverState() {
    // Load resolver state from resources
    try (InputStream stateStream =
        getClass().getClassLoader().getResourceAsStream("state/resolver_state.pb")) {
      if (stateStream == null) {
        throw new RuntimeException("Could not find resolver_state.pb in resources");
      }
      byte[] resolveState = stateStream.readAllBytes();
      this.localResolverApi.setResolverState(resolveState);
    } catch (IOException e) {
      throw new RuntimeException("Failed to load resolver state", e);
    }
  }

  @Override
  public CompletableFuture<ResolveFlagsResponse> resolveFlags(
      String flag, ConfidenceValue.Struct context) {
    final var response =
        localResolverApi.resolve(
            ResolveFlagsRequest.newBuilder()
                .setClientSecret(clientSecret)
                .setApply(false)
                .setEvaluationContext(Struct.newBuilder().putAllFields(context.asProtoMap()))
                .addFlags(flag)
                .build());
    return CompletableFuture.completedFuture(response);
  }
}
