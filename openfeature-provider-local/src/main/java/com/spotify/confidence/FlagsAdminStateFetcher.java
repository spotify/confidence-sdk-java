package com.spotify.confidence;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;

import com.fasterxml.jackson.databind.util.ByteBufferBackedInputStream;
import com.google.protobuf.ByteString;
import com.google.protobuf.Timestamp;
import com.spotify.confidence.shaded.flags.admin.v1.Flag;
import com.spotify.confidence.shaded.flags.admin.v1.ResolverStateServiceGrpc;
import com.spotify.confidence.shaded.flags.admin.v1.ResolverStateUriRequest;
import com.spotify.confidence.shaded.flags.admin.v1.ResolverStateUriResponse;
import com.spotify.confidence.shaded.flags.admin.v1.Segment;
import com.spotify.confidence.shaded.iam.v1.Client;
import com.spotify.confidence.shaded.iam.v1.ClientCredential;
import io.grpc.health.v1.HealthCheckResponse;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.Duration;
import java.time.Instant;
import java.util.BitSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.GZIPInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class FlagsAdminStateFetcher {

  private static final Logger logger = LoggerFactory.getLogger(FlagsAdminStateFetcher.class);

  private final ResolverStateServiceGrpc.ResolverStateServiceBlockingStub resolverStateService;
  private final HealthStatus healthStatus;
  private final String accountName;
  // Source of truth for resolver state, shared with GrpcFlagResolverService
  private final AtomicReference<ResolverState> stateHolder =
      new AtomicReference<>(new ResolverState(Map.of(), Map.of()));
  private final AtomicReference<com.spotify.confidence.shaded.flags.admin.v1.ResolverState>
      rawResolverStateHolder =
          new AtomicReference<>(
              com.spotify.confidence.shaded.flags.admin.v1.ResolverState.newBuilder().build());
  private final AtomicReference<ResolverStateUriResponse> resolverStateUriResponse =
      new AtomicReference<>();
  private final AtomicReference<Instant> refreshTimeHolder = new AtomicReference<>();

  public FlagsAdminStateFetcher(
      ResolverStateServiceGrpc.ResolverStateServiceBlockingStub resolverStateService,
      HealthStatus healthStatus,
      String accountName) {
    this.resolverStateService = resolverStateService;
    this.healthStatus = healthStatus;
    this.accountName = accountName;
  }

  public AtomicReference<ResolverState> stateHolder() {
    return stateHolder;
  }

  public AtomicReference<com.spotify.confidence.shaded.flags.admin.v1.ResolverState>
      rawStateHolder() {
    return rawResolverStateHolder;
  }

  public void reload() {
    final ResolverState currentState = stateHolder.get();
    final Map<String, AccountState> newAccountStates =
        new LinkedHashMap<>(currentState.accountStates());
    final Map<ClientCredential.ClientSecret, AccountClient> secrets = new HashMap<>();

    try {
      final AccountState newAccountState = fetchState();
      newAccountState.flags().forEach(this::logIfSticky);
      newAccountStates.put(accountName, newAccountState);
      secrets.putAll(newAccountState.secrets());
    } catch (Exception e) {
      logger.warn("Failed to reload, ignoring reload", e);
      return;
    }

    stateHolder.set(new ResolverState(newAccountStates, secrets));
    healthStatus.setStatus(HealthCheckResponse.ServingStatus.SERVING);
  }

  private void logIfSticky(String s, Flag flag) {
    if (flag.getRulesList().stream().anyMatch(Flag.Rule::hasMaterializationSpec)) {
      logger.warn(
          "Flag {} is sticky, sticky assignments are not supported in the local resolve", s);
    }
  }

  private String getResolverFileUri() {
    final Instant now = Instant.now();
    if (resolverStateUriResponse.get() == null
        || (refreshTimeHolder.get() == null || refreshTimeHolder.get().isBefore(now))) {
      resolverStateUriResponse.set(
          resolverStateService.resolverStateUri(ResolverStateUriRequest.getDefaultInstance()));
      final var ttl =
          Duration.between(now, toInstant(resolverStateUriResponse.get().getExpireTime()));
      refreshTimeHolder.set(now.plusMillis(ttl.toMillis() / 2));
    }
    return resolverStateUriResponse.get().getSignedUri();
  }

  private Instant toInstant(Timestamp time) {
    return Instant.ofEpochSecond(time.getSeconds(), time.getNanos());
  }

  private AccountState fetchState() {
    final String uri = getResolverFileUri();
    final com.spotify.confidence.shaded.flags.admin.v1.ResolverState state;
    final String etag;
    try {
      final HttpURLConnection conn = (HttpURLConnection) new URL(uri).openConnection();
      if (stateHolder.get() != null && stateHolder.get().accountStates().get(accountName) != null) {
        conn.setRequestProperty(
            "if-none-match", stateHolder.get().accountStates().get(accountName).stateFileHash());
      }
      if (conn.getResponseCode() == 304) {
        // Not modified
        return stateHolder.get().accountStates().get(accountName);
      }
      etag = conn.getHeaderField("etag");
      try (final var stream = conn.getInputStream()) {
        state = com.spotify.confidence.shaded.flags.admin.v1.ResolverState.parseFrom(stream);
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    final List<Flag> flags = state.getFlagsList();
    final Map<String, Flag> flagsIndex = flags.stream().collect(toMap(Flag::getName, identity()));

    final Map<String, BitSet> bitsetsBySegment =
        state.getBitsetsList().stream()
            .collect(
                toMap(
                    com.spotify.confidence.shaded.flags.admin.v1.ResolverState.PackedBitset
                        ::getSegment,
                    bitset ->
                        switch (bitset.getBitsetCase()) {
                          case GZIPPED_BITSET -> unzipBitset(bitset.getGzippedBitset());
                          case FULL_BITSET -> Randomizer.FULL_BITSET;
                          case BITSET_NOT_SET -> throw new UnsupportedOperationException();
                        }));

    final Map<String, Segment> segmentsIndex =
        state.getSegmentsNoBitsetsList().stream().collect(toMap(Segment::getName, c -> c));

    final List<Client> clients = state.getClientsList();
    final Map<ClientCredential.ClientSecret, AccountClient> secrets = new HashMap<>();
    for (Client client : clients) {
      final List<ClientCredential> credentials =
          state.getClientCredentialsList().stream()
              .filter(c -> c.getName().startsWith(client.getName()))
              .toList();
      final Map<ClientCredential.ClientSecret, AccountClient> credentialsIndex =
          credentials.stream()
              .collect(
                  toMap(
                      ClientCredential::getClientSecret,
                      credential -> new AccountClient(accountName, client, credential)));

      secrets.putAll(credentialsIndex);
    }

    logger.info(
        "Loaded {} flags,  {} segments, {} clients, {} secrets for {}",
        flags.size(),
        segmentsIndex.size(),
        clients.size(),
        secrets.size(),
        accountName);

    rawResolverStateHolder.set(state);
    return new AccountState(
        new Account(accountName), flagsIndex, segmentsIndex, bitsetsBySegment, secrets, etag);
  }

  private BitSet unzipBitset(ByteString gzippedBitset) {
    return BitSet.valueOf(uncompressGZIP(gzippedBitset).asReadOnlyByteBuffer());
  }

  public static ByteString uncompressGZIP(final ByteString data) {
    try {
      final InputStream stream =
          new GZIPInputStream(new ByteBufferBackedInputStream(data.asReadOnlyByteBuffer()));
      return ByteString.readFrom(stream);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
