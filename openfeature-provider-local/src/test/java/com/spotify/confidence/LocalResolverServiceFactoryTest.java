package com.spotify.confidence;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.spotify.confidence.shaded.flags.resolver.v1.ResolveFlagsRequest;
import com.spotify.confidence.shaded.flags.resolver.v1.ResolveFlagsResponse;
import com.spotify.confidence.shaded.iam.v1.ClientCredential;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class LocalResolverServiceFactoryTest {

  @Test
  void resolveDelegatesToCurrentWasmApi() throws Exception {
    final WasmResolveApi wasmApi = mock(WasmResolveApi.class);
    final AtomicReference<WasmResolveApi> apiRef = new AtomicReference<>(wasmApi);
    final AtomicReference<ResolverState> resolverState =
        new AtomicReference<>(new ResolverState(java.util.Map.of(), java.util.Map.of()));

    final ResolveTokenConverter tokenConverter = new PlainResolveTokenConverter();
    final ResolveLogger resolveLogger = mock(ResolveLogger.class);
    final AssignLogger assignLogger = mock(AssignLogger.class);

    final LocalResolverServiceFactory factory =
        new LocalResolverServiceFactory(
            apiRef, resolverState, tokenConverter, resolveLogger, assignLogger);

    final ClientCredential.ClientSecret secret =
        ClientCredential.ClientSecret.newBuilder().setSecret("s").build();
    final FlagResolverService service = factory.create(secret);

    final ResolveFlagsRequest request = ResolveFlagsRequest.getDefaultInstance();
    final ResolveFlagsResponse response = ResolveFlagsResponse.getDefaultInstance();
    when(wasmApi.resolve(request)).thenReturn(response);

    final CompletableFuture<ResolveFlagsResponse> future = service.resolveFlags(request);
    final ResolveFlagsResponse actual = future.get();

    assertSame(response, actual);
    verify(wasmApi).resolve(request);
  }

  @Test
  void swapWasmApiInstanceIsObservedByResolver() throws Exception {
    final WasmResolveApi apiA = mock(WasmResolveApi.class);
    final WasmResolveApi apiB = mock(WasmResolveApi.class);
    final AtomicReference<WasmResolveApi> apiRef = new AtomicReference<>(apiA);
    final AtomicReference<ResolverState> resolverState =
        new AtomicReference<>(new ResolverState(java.util.Map.of(), java.util.Map.of()));

    final ResolveTokenConverter tokenConverter = new PlainResolveTokenConverter();
    final ResolveLogger resolveLogger = mock(ResolveLogger.class);
    final AssignLogger assignLogger = mock(AssignLogger.class);

    final LocalResolverServiceFactory factory =
        new LocalResolverServiceFactory(
            apiRef, resolverState, tokenConverter, resolveLogger, assignLogger);

    final ClientCredential.ClientSecret secret =
        ClientCredential.ClientSecret.newBuilder().setSecret("s").build();
    final FlagResolverService service = factory.create(secret);

    final ResolveFlagsRequest request = ResolveFlagsRequest.getDefaultInstance();
    final ResolveFlagsResponse respA =
        ResolveFlagsResponse.newBuilder().setResolveId("test1").build();
    final ResolveFlagsResponse respB =
        ResolveFlagsResponse.newBuilder().setResolveId("test2").build();
    when(apiA.resolve(request)).thenReturn(respA);
    when(apiB.resolve(request)).thenReturn(respB);

    // First call uses apiA
    final ResolveFlagsResponse first = service.resolveFlags(request).get();
    assertSame(respA, first);
    verify(apiA).resolve(request);

    // Swap to apiB and call again
    apiRef.set(apiB);
    final ResolveFlagsResponse second = service.resolveFlags(request).get();
    assertSame(respB, second);
    verify(apiB).resolve(request);
  }
}
