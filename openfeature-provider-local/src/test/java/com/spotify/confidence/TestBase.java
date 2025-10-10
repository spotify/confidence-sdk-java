package com.spotify.confidence;

import static org.mockito.Mockito.mock;

import com.google.protobuf.Struct;
import com.google.protobuf.util.Structs;
import com.google.protobuf.util.Values;
import com.spotify.confidence.shaded.flags.resolver.v1.ResolveFlagsRequest;
import com.spotify.confidence.shaded.flags.resolver.v1.ResolveFlagsResponse;
import com.spotify.confidence.shaded.iam.v1.Client;
import com.spotify.confidence.shaded.iam.v1.ClientCredential;
import java.util.BitSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;

public class TestBase {
  protected static final AtomicReference<ResolverState> resolverState =
      new AtomicReference<>(new ResolverState(Map.of(), Map.of()));

  protected final ResolverFallback mockFallback = mock(ResolverFallback.class);
  protected static final ClientCredential.ClientSecret secret =
      ClientCredential.ClientSecret.newBuilder().setSecret("very-secret").build();
  protected final ResolverState desiredState;
  protected static LocalResolverServiceFactory resolverServiceFactory;
  static final String account = "accounts/foo";
  static final String clientName = "clients/client";
  static final String credentialName = clientName + "/credentials/creddy";
  protected static final Map<ClientCredential.ClientSecret, AccountClient> secrets =
      Map.of(
          secret,
          new AccountClient(
              account,
              Client.newBuilder().setName(clientName).build(),
              ClientCredential.newBuilder()
                  .setName(credentialName)
                  .setClientSecret(secret)
                  .build()));

  protected TestBase(ResolverState state, boolean isWasm) {
    this.desiredState = state;
    final ResolveTokenConverter resolveTokenConverter = new PlainResolveTokenConverter();
    if (isWasm) {
      final var wasmResolverApi =
          new SingleRotatingWasmResolverApi(
              request -> {}, desiredState.toProto().toByteArray(), "", mockFallback);
      resolverServiceFactory =
          new LocalResolverServiceFactory(
              wasmResolverApi, resolverState, resolveTokenConverter, mock(), mockFallback);
    } else {
      resolverServiceFactory =
          new LocalResolverServiceFactory(
              resolverState, resolveTokenConverter, mock(), mockFallback);
    }
  }

  protected static void setup() {}

  @BeforeEach
  protected void setUp() {
    resolverState.set(desiredState);
  }

  protected ResolveFlagsResponse resolveWithContext(
      List<String> flags,
      String username,
      String structFieldName,
      Struct struct,
      boolean apply,
      String secret) {
    try {
      return resolverServiceFactory
          .create(secret)
          .resolveFlags(
              ResolveFlagsRequest.newBuilder()
                  .addAllFlags(flags)
                  .setClientSecret(secret)
                  .setEvaluationContext(
                      Structs.of(
                          "targeting_key", Values.of(username), structFieldName, Values.of(struct)))
                  .setApply(apply)
                  .build())
          .get();
    } catch (InterruptedException | ExecutionException e) {
      throw new RuntimeException(e);
    }
  }

  protected ResolveFlagsResponse resolveWithNumericTargetingKey(
      List<String> flags,
      Number targetingKey,
      String structFieldName,
      Struct struct,
      boolean apply) {
    try {
      final var builder =
          ResolveFlagsRequest.newBuilder()
              .addAllFlags(flags)
              .setClientSecret(secret.getSecret())
              .setApply(apply);

      if (targetingKey instanceof Double || targetingKey instanceof Float) {
        builder.setEvaluationContext(
            Structs.of(
                "targeting_key",
                Values.of(targetingKey.doubleValue()),
                structFieldName,
                Values.of(struct)));
      } else {
        builder.setEvaluationContext(
            Structs.of(
                "targeting_key",
                Values.of(targetingKey.longValue()),
                structFieldName,
                Values.of(struct)));
      }

      return resolverServiceFactory.create(secret.getSecret()).resolveFlags(builder.build()).get();
    } catch (InterruptedException | java.util.concurrent.ExecutionException e) {
      throw new RuntimeException(e);
    }
  }

  protected ResolveFlagsResponse resolveWithContext(
      List<String> flags, String username, String structFieldName, Struct struct, boolean apply) {
    return resolveWithContext(flags, username, structFieldName, struct, apply, secret.getSecret());
  }

  protected static BitSet getBitsetAllSet() {
    final BitSet bitset = new BitSet(1000000);
    bitset.flip(0, bitset.size());

    return bitset;
  }
}
