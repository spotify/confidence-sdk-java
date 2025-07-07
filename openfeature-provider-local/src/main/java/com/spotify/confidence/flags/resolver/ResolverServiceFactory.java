package com.spotify.confidence.flags.resolver;

import com.spotify.confidence.shaded.iam.v1.ClientCredential;

public interface ResolverServiceFactory {
  FlagResolverService create(ClientCredential.ClientSecret clientSecret);

  default FlagResolverService create(String clientSecret) {
    return create(ClientCredential.ClientSecret.newBuilder().setSecret(clientSecret).build());
  }
}
