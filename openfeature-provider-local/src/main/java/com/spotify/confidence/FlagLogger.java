package com.spotify.confidence;

import static com.spotify.confidence.ResolvedValue.resolveToAssignmentReason;

import com.google.protobuf.Struct;
import com.google.protobuf.Timestamp;
import com.spotify.confidence.shaded.flags.resolver.v1.Sdk;
import com.spotify.confidence.shaded.flags.resolver.v1.events.ClientInfo;
import com.spotify.confidence.shaded.flags.resolver.v1.events.FallthroughAssignment;
import com.spotify.confidence.shaded.flags.resolver.v1.events.FlagAssigned;
import java.util.ArrayList;
import java.util.List;

interface FlagLogger {

  void logResolve(
      String resolveId,
      Struct evaluationContext,
      Sdk sdk,
      AccountClient accountClient,
      List<ResolvedValue> values);

  void logAssigns(
      String resolveId, Sdk sdk, List<FlagToApply> flagsToApply, AccountClient accountClient);

  static List<String> getResources(FlagAssigned flagAssigned) {
    final List<String> resources = new ArrayList<>();
    for (var flag : flagAssigned.getFlagsList()) {
      if (flag.hasAssignmentInfo()) {
        if (!flag.getAssignmentInfo().getSegment().isBlank()) {
          resources.add(flag.getAssignmentInfo().getSegment());
        }
        if (!flag.getAssignmentInfo().getVariant().isBlank()) {
          resources.add(flag.getAssignmentInfo().getVariant());
        }
      }

      for (FallthroughAssignment fallthroughAssignment : flag.getFallthroughAssignmentsList()) {
        resources.add(fallthroughAssignment.getRule());
      }

      resources.add(flag.getFlag());
      resources.add(flag.getRule());
    }
    resources.add(flagAssigned.getClientInfo().getClient());
    resources.add(flagAssigned.getClientInfo().getClientCredential());
    return resources;
  }

  static FlagAssigned createFlagAssigned(
      String resolveId, Sdk sdk, List<FlagToApply> flagsToApply, AccountClient accountClient) {
    final var clientInfo =
        ClientInfo.newBuilder()
            .setClient(accountClient.client().getName())
            .setClientCredential(accountClient.clientCredential().getName())
            .setSdk(sdk)
            .build();

    final var builder = FlagAssigned.newBuilder().setResolveId(resolveId).setClientInfo(clientInfo);
    for (var flag : flagsToApply) {
      final var assignedFlag = flag.assignment();
      final FlagAssigned.AppliedFlag.Builder assignedBuilder =
          FlagAssigned.AppliedFlag.newBuilder()
              .setAssignmentId(assignedFlag.getAssignmentId())
              .setFlag(assignedFlag.getFlag())
              .setApplyTime(
                  Timestamp.newBuilder()
                      .setSeconds(flag.skewAdjustedAppliedTime().getEpochSecond())
                      .setNanos(flag.skewAdjustedAppliedTime().getNano())
                      .build())
              .setTargetingKey(assignedFlag.getTargetingKey())
              .setTargetingKeySelector(assignedFlag.getTargetingKeySelector())
              .setRule(assignedFlag.getRule())
              .addAllFallthroughAssignments(assignedFlag.getFallthroughAssignmentsList());

      if (!assignedFlag.getVariant().isBlank()) {
        assignedBuilder.setAssignmentInfo(
            FlagAssigned.AssignmentInfo.newBuilder()
                .setSegment(assignedFlag.getSegment())
                .setVariant(assignedFlag.getVariant())
                .build());
      } else {
        assignedBuilder.setDefaultAssignment(
            FlagAssigned.DefaultAssignment.newBuilder()
                .setReason(resolveToAssignmentReason(assignedFlag.getReason()))
                .build());
      }
      builder.addFlags(assignedBuilder);
    }

    return builder.build();
  }
}
