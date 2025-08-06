package com.spotify.confidence;

import com.spotify.confidence.shaded.iam.v1.Client;
import com.spotify.confidence.shaded.iam.v1.ClientCredential;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

record ResolverState(
    Map<String, AccountState> accountStates,
    Map<ClientCredential.ClientSecret, AccountClient> secrets) {

  public com.spotify.confidence.flags.shaded.admin.v1.ResolverState toProto() {
    final com.spotify.confidence.flags.shaded.admin.v1.ResolverState.Builder builder =
        com.spotify.confidence.flags.shaded.admin.v1.ResolverState.newBuilder();

    // Collect all flags from all account states
    final List<com.spotify.confidence.shaded.flags.admin.v1.Flag> allFlags = new ArrayList<>();
    final List<com.spotify.confidence.shaded.flags.admin.v1.Segment> allSegments =
        new ArrayList<>();
    final List<com.spotify.confidence.flags.shaded.admin.v1.ResolverState.PackedBitset> allBitsets =
        new ArrayList<>();
    final List<Client> allClients = new ArrayList<>();
    final List<com.spotify.confidence.shaded.iam.v1.ClientCredential> allClientCredentials =
        new ArrayList<>();

    // Process each account state
    for (AccountState accountState : accountStates.values()) {
      // Add flags
      allFlags.addAll(accountState.flags().values());

      // Add segments (without bitsets)
      allSegments.addAll(accountState.segments().values());

      // Add bitsets as packed bitsets
      for (Map.Entry<String, BitSet> entry : accountState.bitsets().entrySet()) {
        final String segmentName = entry.getKey();
        final BitSet bitSet = entry.getValue();

        final com.spotify.confidence.flags.shaded.admin.v1.ResolverState.PackedBitset.Builder
            bitsetBuilder =
                com.spotify.confidence.flags.shaded.admin.v1.ResolverState.PackedBitset.newBuilder()
                    .setSegment(segmentName);

        // Check if it's a full bitset (all bits set)
        if (isFullBitset(bitSet)) {
          bitsetBuilder.setFullBitset(true);
        } else {
          // Compress the bitset
          final byte[] compressed = compressBitset(bitSet);
          bitsetBuilder.setGzippedBitset(com.google.protobuf.ByteString.copyFrom(compressed));
        }

        allBitsets.add(bitsetBuilder.build());
      }
    }

    // Process secrets to extract clients and client credentials
    for (AccountClient accountClient : secrets.values()) {
      allClients.add(accountClient.client());
      allClientCredentials.add(accountClient.clientCredential());
    }

    // Set all collected data
    builder.addAllFlags(allFlags);
    builder.addAllSegmentsNoBitsets(allSegments);
    builder.addAllBitsets(allBitsets);
    builder.addAllClients(allClients);
    builder.addAllClientCredentials(allClientCredentials);

    // Set region if available (default to UNSPECIFIED)
    builder.setRegion(
        com.spotify.confidence.flags.shaded.admin.v1.ResolverState.Region.REGION_UNSPECIFIED);

    return builder.build();
  }

  private boolean isFullBitset(BitSet bitSet) {
    // Check if all bits in the bitset are set
    final int cardinality = bitSet.cardinality();
    final int length = bitSet.length();
    return cardinality == length && length > 0;
  }

  private byte[] compressBitset(BitSet bitSet) {
    try (ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
        GZIPOutputStream gzipStream = new GZIPOutputStream(byteStream)) {

      // Convert BitSet to byte array
      final byte[] bytes = bitSet.toByteArray();
      gzipStream.write(bytes);
      gzipStream.finish();

      return byteStream.toByteArray();
    } catch (IOException e) {
      throw new RuntimeException("Failed to compress bitset", e);
    }
  }
}
