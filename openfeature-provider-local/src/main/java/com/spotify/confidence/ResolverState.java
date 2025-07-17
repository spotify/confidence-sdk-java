package com.spotify.confidence;

import com.spotify.confidence.shaded.iam.v1.ClientCredential;
import java.util.Map;

record ResolverState(
    Map<String, AccountState> accountStates,
    Map<ClientCredential.ClientSecret, AccountClient> secrets) {}
