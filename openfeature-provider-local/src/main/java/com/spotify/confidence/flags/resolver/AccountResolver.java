package com.spotify.confidence.flags.resolver;

import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import com.spotify.confidence.flags.resolver.domain.AccountClient;
import com.spotify.confidence.flags.resolver.domain.AccountState;
import com.spotify.confidence.flags.resolver.domain.ResolvedValue;
import com.spotify.confidence.flags.resolver.exceptions.BadRequestException;
import com.spotify.confidence.flags.resolver.exceptions.InternalServerException;
import com.spotify.confidence.shaded.flags.admin.v1.Flag;
import com.spotify.confidence.shaded.flags.admin.v1.Flag.Rule;
import com.spotify.confidence.shaded.flags.admin.v1.Flag.Rule.Assignment;
import com.spotify.confidence.shaded.flags.admin.v1.Flag.Rule.Assignment.AssignmentCase;
import com.spotify.confidence.shaded.flags.admin.v1.Flag.Rule.AssignmentSpec;
import com.spotify.confidence.shaded.flags.admin.v1.Flag.State;
import com.spotify.confidence.shaded.flags.admin.v1.Flag.Variant;
import com.spotify.confidence.shaded.flags.admin.v1.Segment;
import com.spotify.confidence.shaded.flags.resolver.v1.ResolveReason;
import com.spotify.confidence.shaded.flags.types.v1.Targeting;
import com.spotify.confidence.shaded.flags.types.v1.Targeting.Criterion;
import com.spotify.confidence.shaded.flags.types.v1.Targeting.Criterion.AttributeCriterion;
import com.spotify.confidence.shaded.flags.types.v1.Targeting.InnerRule;
import com.spotify.confidence.shaded.flags.types.v1.Targeting.ListValue;
import com.spotify.confidence.shaded.flags.types.v1.Targeting.Value.ValueCase;
import com.spotify.confidence.targeting.TargetingExpr;
import com.spotify.futures.CompletableFutures;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.slf4j.Logger;

public class AccountResolver {
  public static final String TARGETING_KEY = "targeting_key";
  private static final int MAX_NO_OF_FLAGS_TO_BATCH_RESOLVE = 200;
  private final AccountClient client;
  private final AccountState state;
  private final Struct evaluationContext;
  private final Logger logger;

  private static final ResolveFlagListener NO_OP_RESOLVE_FLAG_LISTENER =
      new ResolveFlagListener() {};

  public AccountResolver(
      AccountClient client,
      AccountState state,
      Struct evaluationContext,
      Logger logger,
      Metrics metrics) {
    this.client = client;
    this.state = state;
    this.evaluationContext = evaluationContext;
    this.logger = logger;
  }

  public AccountClient getClient() {
    return client;
  }

  public Struct getEvaluationContext() {
    return evaluationContext;
  }

  public CompletableFuture<List<ResolvedValue>> resolveFlags(List<String> flagNames) {
    return resolveFlags(flagNames, NO_OP_RESOLVE_FLAG_LISTENER);
  }

  public CompletableFuture<List<ResolvedValue>> resolveFlags(
      List<String> flagNames, ResolveFlagListener listener) {
    return resolveFlags(flagNames, listener, false);
  }

  public CompletableFuture<List<ResolvedValue>> resolveFlags(
      List<String> flagNames, ResolveFlagListener listener, boolean strict) {
    final List<Flag> flagsToResolve =
        state.flags().values().stream()
            .filter(flag -> flag.getClientsList().contains(client.client().getName()))
            .filter(flag -> flag.getState() == State.ACTIVE)
            .filter(flag -> flagNames.isEmpty() || flagNames.contains(flag.getName()))
            .toList();

    if (strict && flagsToResolve.isEmpty() && !flagNames.isEmpty()) {
      // explicitly declared flags to resolve but no active flags found
      throw new BadRequestException(
          "No active flags found for the client %s with the names %s"
              .formatted(client.client().getName(), flagNames));
    }
    if (flagsToResolve.size() > MAX_NO_OF_FLAGS_TO_BATCH_RESOLVE) {
      throw new BadRequestException(
          "Max %d flags allowed in a single resolve request, this request would return %d flags."
              .formatted(MAX_NO_OF_FLAGS_TO_BATCH_RESOLVE, flagsToResolve.size()));
    }
    final ConcurrentMap<ConvertValueCacheKey, ListValue> convertValueCache =
        new ConcurrentHashMap<>();

    final var futures =
        flagsToResolve.stream()
            .map(
                flag -> {
                  try {
                    return resolveFlag(flag, listener, convertValueCache);
                  } catch (BadRequestException badRequestException) {
                    return CompletableFuture.<ResolvedValue>failedFuture(badRequestException);
                  } catch (RuntimeException error) {
                    logger.error("Error during resolve", error);
                    return CompletableFuture.completedFuture(
                        new ResolvedValue(flag).withReason(ResolveReason.RESOLVE_REASON_ERROR));
                  }
                })
            .toList();
    return CompletableFutures.allAsList(futures);
  }

  private CompletableFuture<ResolvedValue> resolveFlag(
      final Flag flag,
      ResolveFlagListener listener,
      ConcurrentMap<ConvertValueCacheKey, ListValue> convertValueCache) {
    ResolvedValue resolvedValue = new ResolvedValue(flag);

    if (flag.getState() == State.ARCHIVED) {
      return CompletableFuture.completedFuture(
          resolvedValue.withReason(ResolveReason.RESOLVE_REASON_FLAG_ARCHIVED));
    }

    for (Rule rule : flag.getRulesList()) {
      if (!rule.getEnabled()) {
        listener.markRuleEvaluationReason(
            rule.getName(), ResolveFlagListener.RuleEvaluationReason.RULE_NOT_ENABLED);
        continue;
      }

      final String segmentName = rule.getSegment();
      final Segment segment = state.segments().get(segmentName);
      if (segment == null) {
        logger.warn("Segment {} not found among active segments", rule.getSegment());
        listener.markRuleEvaluationReason(
            rule.getName(),
            ResolveFlagListener.RuleEvaluationReason.SEGMENT_NOT_FOUND_OR_NOT_ACTIVE);
        continue;
      }

      final String targetingKey =
          rule.getTargetingKeySelector().isBlank() ? TARGETING_KEY : rule.getTargetingKeySelector();
      final Value unitValue = EvalUtil.getAttributeValue(evaluationContext, targetingKey);
      if (unitValue.hasNullValue()) {
        listener.markRuleEvaluationReason(
            rule.getName(), ResolveFlagListener.RuleEvaluationReason.SEGMENT_NOT_MATCHED);
        listener.markSegmentEvaluationReason(
            flag.getName(),
            segment.getName(),
            ResolveFlagListener.SegmentEvaluationReason.TARGETING_KEY_MISSING);
        continue;
      }

      if (unitValue.getKindCase() != Value.KindCase.STRING_VALUE) {
        return CompletableFuture.completedFuture(
            resolvedValue.withReason(ResolveReason.RESOLVE_REASON_TARGETING_KEY_ERROR));
      }
      final String unit = unitValue.getStringValue();
      if (unit.length() > 100) {
        throw new BadRequestException("Targeting key is too larger, max 100 characters.");
      }

      if (!segmentMatches(
          segment, segmentName, unit, flag.getName(), listener, convertValueCache)) {
        listener.markRuleEvaluationReason(
            rule.getName(), ResolveFlagListener.RuleEvaluationReason.SEGMENT_NOT_MATCHED);
        continue;
      }

      listener.markSegmentEvaluationReason(
          flag.getName(),
          segment.getName(),
          ResolveFlagListener.SegmentEvaluationReason.SEGMENT_MATCHED);

      final AssignmentSpec spec = rule.getAssignmentSpec();
      final int bucketCount = spec.getBucketCount();

      // hash bucket for targetingKey with salt from segment
      final String variantSalt = segmentName.split("/")[1];
      final long bucket = Randomizer.getBucket(unit, variantSalt, bucketCount);

      final Optional<Assignment> matchedAssignmentOpt =
          spec.getAssignmentsList().stream()
              .filter(variant -> Randomizer.coversBucket(variant, bucket))
              .findFirst();

      if (matchedAssignmentOpt.isPresent()) {
        final Assignment matchedAssignment = matchedAssignmentOpt.get();
        if (matchedAssignment.getAssignmentCase() == AssignmentCase.FALLTHROUGH) {
          listener.markRuleEvaluationReason(
              rule.getName(), ResolveFlagListener.RuleEvaluationReason.RULE_MATCHED_FALLTHROUGH);
          resolvedValue =
              resolvedValue.attributeFallthroughRule(
                  rule, matchedAssignment.getAssignmentId(), unit);
          continue;
        }

        if (matchedAssignment.getAssignmentCase() == AssignmentCase.CLIENT_DEFAULT) {
          return CompletableFuture.completedFuture(
              resolvedValue.withClientDefaultMatch(
                  matchedAssignment.getAssignmentId(), unit, segment, rule));
        }

        if (matchedAssignment.getAssignmentCase() == AssignmentCase.VARIANT) {
          return CompletableFuture.completedFuture(
              variantMatch(resolvedValue, matchedAssignment, unit, segment, rule, flag));
        }
      } else {
        listener.markRuleEvaluationReason(
            rule.getName(),
            ResolveFlagListener.RuleEvaluationReason.RULE_EVALUATED_NO_VARIANT_MATCH);
      }
    }

    return CompletableFuture.completedFuture(
        resolvedValue.withReason(ResolveReason.RESOLVE_REASON_NO_SEGMENT_MATCH));
  }

  private ResolvedValue variantMatch(
      ResolvedValue resolvedValue,
      Assignment matchedAssignment,
      String unit,
      Segment segment,
      Rule rule,
      Flag flag) {
    final String variantName = matchedAssignment.getVariant().getVariant();
    final Flag.Variant variant = findVariantOrThrow(flag.getVariantsList(), variantName);

    return resolvedValue.withMatch(
        matchedAssignment.getAssignmentId(), variantName, unit, variant.getValue(), segment, rule);
  }

  private boolean isTargetingMatch(
      Segment segment,
      final String unit,
      Set<String> visitedSegments,
      final String flag,
      ResolveFlagListener listener,
      ConcurrentMap<ConvertValueCacheKey, ListValue> convertValueCache) {
    final TargetingExpr targetingExpr = state.getTargetingExpr(segment);
    final Set<String> satisfiedRefs = new HashSet<>(targetingExpr.refs().size());
    for (var entry : targetingExpr.refs().entrySet()) {
      final boolean matches =
          switch (entry.getValue().getCriterionCase()) {
            case ATTRIBUTE -> {
              final AttributeCriterion criterion = entry.getValue().getAttribute();
              final ValueCase expectedPrimitiveType =
                  getExpectedPrimitiveType(criterion, segment, entry);
              final Value attributeValue =
                  EvalUtil.getAttributeValue(evaluationContext, criterion.getAttributeName());

              if (attributeValue.hasNullValue()) {
                listener.addEvalContextMissingValue(
                    flag, segment.getName(), criterion.getAttributeName());
              }

              final ListValue convertedValueList =
                  convertValueCache.computeIfAbsent(
                      new ConvertValueCacheKey(attributeValue, expectedPrimitiveType),
                      ignored ->
                          listWrapper(
                              EvalUtil.convertToTargetingValue(
                                  attributeValue, expectedPrimitiveType)));
              yield resolveAttributeCriterion(segment, entry, criterion, convertedValueList);
            }
            case SEGMENT -> {
              final var segmentName = entry.getValue().getSegment().getSegment();
              yield segmentMatches(
                  state.segments().get(segmentName),
                  segmentName,
                  unit,
                  visitedSegments,
                  flag,
                  listener,
                  convertValueCache);
            }
            default -> throw new UnsupportedOperationException();
          };
      if (matches) {
        satisfiedRefs.add(entry.getKey());
      }
    }
    return targetingExpr.eval(satisfiedRefs);
  }

  private static boolean resolveAttributeCriterion(
      Segment segment,
      Entry<String, Criterion> entry,
      AttributeCriterion criterion,
      ListValue convertedValueList) {
    return switch (criterion.getRuleCase()) {
      case EQ_RULE -> convertedValueList.getValuesList().contains(criterion.getEqRule().getValue());
      case SET_RULE ->
          convertedValueList.getValuesList().stream()
              .anyMatch(v -> criterion.getSetRule().getValuesList().contains(v));
      case RANGE_RULE ->
          convertedValueList.getValuesList().stream()
              .anyMatch(v -> EvalUtil.isInRange(criterion.getRangeRule(), v));
      case ANY_RULE ->
          convertedValueList.getValuesList().stream()
              .anyMatch(
                  v ->
                      resolveAttributeCriterion(
                          segment, entry, criterion.getAnyRule().getRule(), v));
      case ALL_RULE ->
          convertedValueList.getValuesList().stream()
              .allMatch(
                  v ->
                      resolveAttributeCriterion(
                          segment, entry, criterion.getAllRule().getRule(), v));
      default ->
          throw new BadRequestException(
              "Targeting rule %s in %s is invalid".formatted(entry.getValue(), segment.getName()));
    };
  }

  private static boolean resolveAttributeCriterion(
      Segment segment,
      Entry<String, Criterion> entry,
      InnerRule rule,
      Targeting.Value convertedValue) {
    return switch (rule.getRuleCase()) {
      case EQ_RULE -> rule.getEqRule().getValue().equals(convertedValue);
      case SET_RULE -> rule.getSetRule().getValuesList().contains(convertedValue);
      case RANGE_RULE -> EvalUtil.isInRange(rule.getRangeRule(), convertedValue);
      default ->
          throw new BadRequestException(
              "Targeting rule %s in %s is invalid".formatted(entry.getValue(), segment.getName()));
    };
  }

  private static ListValue listWrapper(Targeting.Value value) {
    if (value == null) {
      return ListValue.getDefaultInstance();
    } else if (value.hasListValue()) {
      return value.getListValue();
    } else {
      return ListValue.newBuilder().addValues(value).build();
    }
  }

  private boolean segmentMatches(
      Segment segment,
      String segmentName,
      String unit,
      String flag,
      ResolveFlagListener listener,
      ConcurrentMap<ConvertValueCacheKey, ListValue> convertValueCache) {
    return segmentMatches(
        segment, segmentName, unit, new HashSet<>(), flag, listener, convertValueCache);
  }

  private boolean segmentMatches(
      Segment segment,
      String segmentName,
      String unit,
      Set<String> visitedSegments,
      String flag,
      ResolveFlagListener listener,
      ConcurrentMap<ConvertValueCacheKey, ListValue> convertValueCache) {
    if (visitedSegments.contains(segmentName)) {
      throw new InternalServerException(
          "Segment %s has a circular dependency".formatted(segmentName));
    }
    visitedSegments.add(segmentName);

    // handle targeting
    try {
      final boolean targetingMatch =
          isTargetingMatch(segment, unit, visitedSegments, flag, listener, convertValueCache);
      if (!targetingMatch) {
        listener.markSegmentEvaluationReason(
            flag, segmentName, ResolveFlagListener.SegmentEvaluationReason.TARGETING_NOT_MATCHED);
        return false;
      }
    } catch (RuntimeException error) {
      logger.error("Error during targeting", error);
      return false;
    }

    final boolean inBitset = Randomizer.inBitset(state, segmentName, unit);

    if (!inBitset) {
      listener.markSegmentEvaluationReason(
          flag, segmentName, ResolveFlagListener.SegmentEvaluationReason.BITSET_NOT_MATCHED);
    }

    return inBitset;
  }

  private ValueCase getExpectedPrimitiveType(
      AttributeCriterion criterion, Segment segment, Entry<String, Criterion> entry) {
    return switch (criterion.getRuleCase()) {
      case EQ_RULE -> criterion.getEqRule().getValue().getValueCase();
      case SET_RULE ->
          criterion.getSetRule().getValuesList().isEmpty()
              ? ValueCase.VALUE_NOT_SET
              : criterion.getSetRule().getValuesList().get(0).getValueCase();
      case RANGE_RULE -> getExpectedPrimitiveType(criterion.getRangeRule());
      case ANY_RULE -> getExpectedPrimitiveType(criterion.getAnyRule().getRule(), segment, entry);
      case ALL_RULE -> getExpectedPrimitiveType(criterion.getAllRule().getRule(), segment, entry);
      default ->
          throw new BadRequestException(
              "Targeting rule %s in %s has invalid type"
                  .formatted(entry.getValue(), segment.getName()));
    };
  }

  private ValueCase getExpectedPrimitiveType(
      InnerRule rule, Segment segment, Entry<String, Criterion> entry) {
    return switch (rule.getRuleCase()) {
      case EQ_RULE -> rule.getEqRule().getValue().getValueCase();
      case SET_RULE ->
          rule.getSetRule().getValuesList().isEmpty()
              ? ValueCase.VALUE_NOT_SET
              : rule.getSetRule().getValuesList().get(0).getValueCase();
      case RANGE_RULE -> getExpectedPrimitiveType(rule.getRangeRule());
      default ->
          throw new BadRequestException(
              "Targeting rule %s in %s has invalid type"
                  .formatted(entry.getValue(), segment.getName()));
    };
  }

  private static ValueCase getExpectedPrimitiveType(Targeting.RangeRule rangeRule) {
    if (rangeRule.hasStartInclusive()) {
      return rangeRule.getStartInclusive().getValueCase();
    } else if (rangeRule.hasStartExclusive()) {
      return rangeRule.getStartExclusive().getValueCase();
    } else if (rangeRule.hasEndInclusive()) {
      return rangeRule.getEndInclusive().getValueCase();
    } else if (rangeRule.hasEndExclusive()) {
      return rangeRule.getEndExclusive().getValueCase();
    } else {
      return ValueCase.VALUE_NOT_SET;
    }
  }

  private static Flag.Variant findVariantOrThrow(
      Collection<Variant> variantsList, String searchedVariant) {
    return variantsList.stream()
        .filter(variant -> searchedVariant.equals(variant.getName()))
        .findFirst()
        .orElseThrow(
            () ->
                new BadRequestException("Assigned flag variant " + searchedVariant + " not found"));
  }

  private record ConvertValueCacheKey(Value value, Targeting.Value.ValueCase expectedType) {}
}
