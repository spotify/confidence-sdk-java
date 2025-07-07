package com.spotify.confidence.flags.resolver.domain;

import com.spotify.confidence.shaded.iam.v1.ClientCredential;
import java.util.Map;

public record ResolverState(
    Map<String, AccountState> accountStates,
    Map<ClientCredential.ClientSecret, AccountClient> secrets) {}
