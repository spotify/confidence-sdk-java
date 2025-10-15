package com.spotify.confidence;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dylibso.chicory.wasm.ChicoryException;
import com.spotify.confidence.shaded.flags.resolver.v1.ResolveFlagsRequest;
import com.spotify.confidence.shaded.flags.resolver.v1.ResolveFlagsResponse;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

public class SwapWasmResolverApiTest {

  @Test
  public void testChicoryExceptionTriggersStateReset() throws Exception {
    // Create a mock WasmFlagLogger
    final WasmFlagLogger mockLogger = mock(WasmFlagLogger.class);

    // Use valid test state bytes and accountId
    final byte[] initialState = ResolveTest.exampleStateBytes;
    final String accountId = "test-account-id";

    // Create a spy of SwapWasmResolverApi to verify method calls
    final SwapWasmResolverApi swapApi =
        spy(
            new SwapWasmResolverApi(
                mockLogger, initialState, accountId, mock(ResolverFallback.class)));

    // Create a mock WasmResolveApi that throws ChicoryException
    final WasmResolveApi mockWasmApi = mock(WasmResolveApi.class);
    when(mockWasmApi.resolve(any(ResolveFlagsRequest.class)))
        .thenThrow(new ChicoryException("WASM runtime error"));

    // Replace the internal WasmResolveApi with our mock
    final var field = SwapWasmResolverApi.class.getDeclaredField("wasmResolverApiRef");
    field.setAccessible(true);
    @SuppressWarnings("unchecked")
    final AtomicReference<WasmResolveApi> ref =
        (AtomicReference<WasmResolveApi>) field.get(swapApi);
    ref.set(mockWasmApi);

    // Create a test resolve request
    final ResolveFlagsRequest request =
        ResolveFlagsRequest.newBuilder()
            .addFlags("test-flag")
            .setClientSecret("test-secret")
            .build();

    // Execute the resolve and expect ChicoryException to be thrown
    assertThrows(ChicoryException.class, () -> swapApi.resolve(request));

    // Verify that updateStateAndFlushLogs was called with null state and the accountId
    verify(swapApi, atLeastOnce()).updateStateAndFlushLogs(null, accountId);
  }

  @Test
  public void testIsClosedExceptionTriggersRetry() throws Exception {
    // Create a mock WasmFlagLogger
    final WasmFlagLogger mockLogger = mock(WasmFlagLogger.class);

    // Use valid test state bytes and accountId
    final byte[] initialState = ResolveTest.exampleStateBytes;
    final String accountId = "test-account-id";

    // Create a spy of SwapWasmResolverApi to verify method calls
    final SwapWasmResolverApi swapApi =
        spy(
            new SwapWasmResolverApi(
                mockLogger, initialState, accountId, mock(ResolverFallback.class)));

    // Create a mock WasmResolveApi that throws IsClosedException on first call, then succeeds
    final WasmResolveApi mockWasmApi = mock(WasmResolveApi.class);
    final ResolveFlagsResponse mockResponse = ResolveFlagsResponse.newBuilder().build();
    when(mockWasmApi.resolve(any(ResolveFlagsRequest.class)))
        .thenThrow(new IsClosedException())
        .thenReturn(mockResponse);

    // Replace the internal WasmResolveApi with our mock
    final var field = SwapWasmResolverApi.class.getDeclaredField("wasmResolverApiRef");
    field.setAccessible(true);
    @SuppressWarnings("unchecked")
    final AtomicReference<WasmResolveApi> ref =
        (AtomicReference<WasmResolveApi>) field.get(swapApi);
    ref.set(mockWasmApi);

    // Create a test resolve request
    final ResolveFlagsRequest request =
        ResolveFlagsRequest.newBuilder()
            .addFlags("flags/flag-1")
            .setClientSecret(TestBase.secret.getSecret())
            .build();

    // Call resolve - it should retry when IsClosedException is thrown and succeed on second attempt
    final ResolveFlagsResponse response = swapApi.resolve(request);

    // Verify response is not null
    assertNotNull(response);

    // Verify that the mock WasmResolveApi.resolve was called twice (first threw exception, second
    // succeeded)
    verify(mockWasmApi, times(2)).resolve(request);
  }
}
