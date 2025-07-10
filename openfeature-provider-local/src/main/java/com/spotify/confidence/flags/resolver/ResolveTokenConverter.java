package com.spotify.confidence.flags.resolver;

import com.google.protobuf.ByteString;
import com.google.protobuf.Struct;
import com.spotify.confidence.flags.resolver.domain.AssignmentMatch;
import com.spotify.confidence.flags.resolver.domain.ResolvedValue;
import com.spotify.confidence.shaded.flags.resolver.v1.ResolveToken;
import com.spotify.confidence.shaded.flags.resolver.v1.ResolveTokenV1;
import com.spotify.confidence.shaded.flags.resolver.v1.ResolveTokenV1.AssignedFlag;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public abstract class ResolveTokenConverter {

  public ByteString createResolveToken(
      String accountName,
      String resolveId,
      List<ResolvedValue> resolvedValues,
      Struct evaluationContext) {
    final Map<String, AssignedFlag> assignedFlags =
        resolvedValues.stream()
            .map(ResolveTokenConverter::toAssignedFlag)
            .collect(Collectors.toMap(AssignedFlag::getFlag, Function.identity()));

    final ResolveToken resolveToken =
        ResolveToken.newBuilder()
            .setTokenV1(
                ResolveTokenV1.newBuilder()
                    .setAccount(accountName)
                    .setResolveId(resolveId)
                    .setEvaluationContext(evaluationContext)
                    .putAllAssignments(assignedFlags)
                    .build())
            .build();

    return convertResolveToken(resolveToken);
  }

  public abstract ByteString convertResolveToken(ResolveToken resolveToken);

  public abstract ResolveToken readResolveToken(ByteString token);

  public static AssignedFlag toAssignedFlag(ResolvedValue value) {
    final var builder =
        AssignedFlag.newBuilder()
            .setFlag(value.flag().getName())
            .setReason(value.reason())
            .addAllFallthroughAssignments(value.fallthroughAssignments());

    if (value.matchedAssignment().isEmpty()) {
      return builder.build();
    }

    final AssignmentMatch match = value.matchedAssignment().get();

    return builder
        .setAssignmentId(match.assignmentId())
        .setRule(match.matchedRule().getName())
        .setSegment(match.segment().getName())
        .setVariant(match.variant().orElse(""))
        .setTargetingKey(match.targetingKey())
        .setTargetingKeySelector(match.matchedRule().getTargetingKeySelector())
        .build();
  }
}
