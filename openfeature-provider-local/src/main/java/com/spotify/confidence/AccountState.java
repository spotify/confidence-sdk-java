package com.spotify.confidence;

import static com.spotify.confidence.Randomizer.MEGA_SALT;

import com.spotify.confidence.expressions.TargetingExpr;
import com.spotify.confidence.shaded.flags.admin.v1.Flag;
import com.spotify.confidence.shaded.flags.admin.v1.Segment;
import com.spotify.confidence.shaded.iam.v1.ClientCredential.ClientSecret;
import java.util.BitSet;
import java.util.Map;
import java.util.stream.Collectors;

record AccountState(
    Account account,
    Map<String, Flag> flags,
    Map<String, Segment> segments,
    Map<String, BitSet> bitsets,
    Map<ClientSecret, AccountClient> secrets,
    String stateFileHash,
    Map<String, TargetingExpr> targeting,
    String saltForAccount) {
  AccountState {
    flags =
        flags.entrySet().stream()
            .collect(
                Collectors.toMap(
                    Map.Entry::getKey,
                    e -> {
                      final var builder = e.getValue().toBuilder();
                      for (var variant : builder.getVariantsBuilderList()) {
                        variant.setValue(
                            EvalUtil.expandToSchema(variant.getValue(), e.getValue().getSchema()));
                      }
                      return builder.build();
                    }));
  }

  AccountState(
      Account account,
      Map<String, Flag> flags,
      Map<String, Segment> segments,
      Map<String, BitSet> bitsets,
      Map<ClientSecret, AccountClient> secrets,
      String stateFileHash) {
    this(
        account,
        flags,
        segments,
        bitsets,
        secrets,
        stateFileHash,
        segments.entrySet().stream()
            .collect(
                Collectors.toMap(
                    Map.Entry::getKey,
                    segment -> TargetingExpr.fromTargeting(segment.getValue().getTargeting()))),
        "%s-%s".formatted(MEGA_SALT, account.name().split("/")[1]));
  }

  TargetingExpr getTargetingExpr(String segmentName) {
    return targeting.get(segmentName);
  }

  TargetingExpr getTargetingExpr(Segment segment) {
    return getTargetingExpr(segment.getName());
  }
}
