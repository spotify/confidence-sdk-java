package com.spotify.confidence.flags.resolver;

public interface ResolveFlagListener {

  enum RuleEvaluationReason {
    RULE_MATCHED,
    RULE_EVALUATED_NO_VARIANT_MATCH,
    RULE_NOT_ENABLED,
    RULE_NOT_EVALUATED,
    SEGMENT_NOT_FOUND_OR_NOT_ACTIVE,
    SEGMENT_NOT_MATCHED,
    RULE_MATCHED_FALLTHROUGH,
    MATERIALIZATION_NOT_MATCHED,
    MATERIALIZATION_AND_SEGMENT_NOT_MATCHED
  }

  enum SegmentEvaluationReason {
    SEGMENT_MATCHED,
    SEGMENT_NOT_EVALUATED,
    TARGETING_NOT_MATCHED,
    BITSET_NOT_MATCHED,
    TARGETING_KEY_MISSING,
  }

  default void markRuleEvaluationReason(String rule, RuleEvaluationReason reason) {}

  default void markSegmentEvaluationReason(
      String flag, String segment, SegmentEvaluationReason reason) {}

  default void addEvalContextMissingValue(String flag, String segment, String fieldName) {}
}
