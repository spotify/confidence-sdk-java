package com.spotify.confidence;

import static org.mockito.Mockito.mock;

import com.google.protobuf.Struct;
import com.google.protobuf.util.Structs;
import com.google.protobuf.util.Values;
import com.spotify.confidence.flags.resolver.ResolveTokenConverter;
import com.spotify.confidence.flags.resolver.ResolverServiceFactory;
import com.spotify.confidence.flags.resolver.SidecarResolverServiceFactory;
import com.spotify.confidence.flags.resolver.domain.AccountClient;
import com.spotify.confidence.flags.resolver.domain.ResolverState;
import com.spotify.confidence.flags.resolver.util.PlainResolveTokenConverter;
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

  protected TestBase(ResolverState state) {
    this.desiredState = state;
  }

  public static void setup() {
    final ResolveTokenConverter resolveTokenConverter = new PlainResolveTokenConverter();
    resolverServiceFactory =
        new SidecarResolverServiceFactory(resolverState, resolveTokenConverter, mock(), mock());
  }

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
