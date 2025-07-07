package com.spotify.confidence.flags.resolver.domain;

import com.spotify.confidence.iam.v1.ClientCredential.ClientSecret;
import java.util.Map;

public record ResolverState(
    Map<String, AccountState> accountStates, Map<ClientSecret, AccountClient> secrets) {}
