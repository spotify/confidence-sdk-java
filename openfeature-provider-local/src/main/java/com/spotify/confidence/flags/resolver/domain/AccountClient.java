package com.spotify.confidence.flags.resolver.domain;

import com.spotify.confidence.shaded.iam.v1.Client;
import com.spotify.confidence.shaded.iam.v1.ClientCredential;

public record AccountClient(String accountName, Client client, ClientCredential clientCredential) {}
