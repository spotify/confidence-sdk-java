package com.spotify.confidence.flags.resolver.domain;

import com.google.protobuf.Struct;
import com.spotify.confidence.shaded.flags.admin.v1.Flag;
import com.spotify.confidence.shaded.flags.admin.v1.Segment;
import com.spotify.confidence.shaded.flags.resolver.v1.ResolveReason;
import com.spotify.confidence.shaded.flags.resolver.v1.events.FallthroughAssignment;
import com.spotify.confidence.shaded.flags.resolver.v1.events.FlagAssigned.DefaultAssignment.DefaultAssignmentReason;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public record ResolvedValue(
    Flag flag,
    ResolveReason reason,
    Optional<AssignmentMatch> matchedAssignment,
    List<FallthroughRule> fallthroughRules) {
  public ResolvedValue(Flag flag) {
    this(flag, ResolveReason.RESOLVE_REASON_NO_SEGMENT_MATCH, Optional.empty(), List.of());
  }

  public ResolvedValue withReason(ResolveReason reason) {
    return new ResolvedValue(flag, reason, matchedAssignment, fallthroughRules);
  }

  public ResolvedValue withMatch(
      String assignmentId,
      String variant,
      String unit,
      Struct value,
      Segment segment,
      Flag.Rule matchedRule) {
    return new ResolvedValue(
        flag,
        ResolveReason.RESOLVE_REASON_MATCH,
        Optional.of(
            new AssignmentMatch(
                assignmentId,
                unit,
                Optional.of(variant),
                Optional.of(value),
                segment,
                matchedRule)),
        fallthroughRules);
  }

  public ResolvedValue withClientDefaultMatch(
      String assignmentId, String unit, Segment segment, Flag.Rule matchedRule) {
    return new ResolvedValue(
        flag,
        ResolveReason.RESOLVE_REASON_MATCH,
        Optional.of(
            new AssignmentMatch(
                assignmentId, unit, Optional.empty(), Optional.empty(), segment, matchedRule)),
        fallthroughRules);
  }

  public static DefaultAssignmentReason resolveToAssignmentReason(ResolveReason reason) {
    return switch (reason) {
      case RESOLVE_REASON_NO_SEGMENT_MATCH -> DefaultAssignmentReason.NO_SEGMENT_MATCH;
      case RESOLVE_REASON_NO_TREATMENT_MATCH -> DefaultAssignmentReason.NO_TREATMENT_MATCH;
      case RESOLVE_REASON_FLAG_ARCHIVED -> DefaultAssignmentReason.FLAG_ARCHIVED;
      default -> DefaultAssignmentReason.DEFAULT_ASSIGNMENT_REASON_UNSPECIFIED;
    };
  }

  public ResolvedValue attributeFallthroughRule(Flag.Rule rule, String assignmentId, String unit) {
    final List<FallthroughRule> attributed = new ArrayList<>(fallthroughRules);
    attributed.add(new FallthroughRule(rule, assignmentId, unit));
    return new ResolvedValue(flag, reason, matchedAssignment, attributed);
  }

  public List<FallthroughAssignment> fallthroughAssignments() {
    return fallthroughRules().stream()
        .map(
            assignment ->
                FallthroughAssignment.newBuilder()
                    .setAssignmentId(assignment.assignmentId())
                    .setRule(assignment.rule().getName())
                    .setTargetingKey(assignment.targetingKey())
                    .setTargetingKeySelector(assignment.rule().getTargetingKeySelector())
                    .build())
        .toList();
  }
}
