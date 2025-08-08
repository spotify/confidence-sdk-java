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

  protected static final ClientCredential.ClientSecret secret =
      ClientCredential.ClientSecret.newBuilder().setSecret("very-secret").build();
  private final ResolverState desiredState;
  private static ResolverServiceFactory resolverServiceFactory;
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
      final var wasmResolverApi = new WasmResolveApi(mock());
      wasmResolverApi.setResolverState(desiredState.toProto().toByteArray());
      resolverServiceFactory =
          new LocalResolverServiceFactory(
              wasmResolverApi, resolverState, resolveTokenConverter, mock(), mock());
    } else {
      resolverServiceFactory =
          new LocalResolverServiceFactory(
              null, resolverState, resolveTokenConverter, mock(), mock());
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
