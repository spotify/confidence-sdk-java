package com.spotify.confidence.targeting;

import static com.spotify.confidence.targeting.Expr.T;
import static com.spotify.confidence.targeting.Expr.and;
import static com.spotify.confidence.targeting.Expr.not;
import static com.spotify.confidence.targeting.Expr.or;
import static com.spotify.confidence.targeting.Expr.ref;
import static com.spotify.confidence.targeting.Util.evalExpression;
import static java.util.Objects.requireNonNull;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

import com.spotify.confidence.shaded.flags.types.v1.Expression;
import com.spotify.confidence.shaded.flags.types.v1.Targeting;
import com.spotify.confidence.shaded.flags.types.v1.Targeting.Criterion;
import com.spotify.confidence.shaded.flags.types.v1.Targeting.Criterion.AttributeCriterion;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

public class TargetingExpr {

  private final Expr expression;
  private final Map<String, Criterion> refs;

  TargetingExpr(final Expr expression, final Map<String, Criterion> refs) {
    // todo: check invariants
    // . all refs in expression exist as keys in the refs map

    this.expression = requireNonNull(expression);
    this.refs = requireNonNull(refs);
  }

  public Expr expression() {
    return expression;
  }

  public Map<String, Criterion> refs() {
    return refs;
  }

  public Map<String, AttributeCriterion> attributeRefs() {
    return refs.entrySet().stream()
        .filter(c -> c.getValue().hasAttribute())
        .collect(Collectors.toMap(Map.Entry::getKey, k -> k.getValue().getAttribute()));
  }

  /**
   * Conjugates this expression with another one (this AND other), handling name collisions.
   *
   * @param other The other expression to conjugate
   * @return A new instance which represents the conjunction of both expressions
   */
  public TargetingExpr andWith(final TargetingExpr other) {
    final Set<String> refs1 = new HashSet<>();
    final Set<String> refs2 = new HashSet<>();
    collectRefs(expression, refs1);
    collectRefs(other.expression, refs2);

    final RefMapper refMapper = RefMapper.create(refs1, refs2);
    final Map<String, String> refs1Map =
        refs1.stream().collect(toMap(identity(), refMapper::mapRef));
    final Map<String, String> refs2Map =
        refs2.stream().collect(toMap(identity(), refMapper::mapRef));

    final Expr conjunction =
        ExprNormalizer.normalize(
            and(mapRefs(expression, refs1Map), mapRefs(other.expression, refs2Map)));

    final Map<String, Criterion> newRefs = new HashMap<>();
    newRefs.putAll(mapCriterionRefs(refs, refs1Map));
    newRefs.putAll(mapCriterionRefs(other.refs, refs2Map));

    // todo: merge ref-names for identical rules from t1,t2

    return new TargetingExpr(conjunction, Map.copyOf(newRefs));
  }

  public boolean eval(Set<String> trueRefs) {
    return evalExpression(expression, trueRefs);
  }

  /**
   * Rewrite criterion from the expression, potentially also re-mapping the criterion ref name.
   *
   * @param mapper A mapper function that can optionally re-map a criterion
   * @return A new instance where zero or more criterion have been changed
   * @throws IllegalStateException if multiple criterion were mapped to the same name
   */
  public TargetingExpr rewriteCriteria(
      final BiFunction<String, Criterion, Optional<CriterionEntry>> mapper) {
    final Map<String, Criterion> newRefs = new LinkedHashMap<>();
    final Map<String, String> renames = new HashMap<>();
    refs.forEach(
        (name, value) -> {
          final Optional<CriterionEntry> rewritten = mapper.apply(name, value);
          if (rewritten.isPresent()) {
            final String newName = rewritten.get().name();
            if (newRefs.containsKey(newName)) {
              throw new IllegalStateException("Name " + newName + " already specified");
            }
            newRefs.put(newName, rewritten.get().criterion());
            renames.put(name, newName);
          } else {
            if (newRefs.containsKey(name)) {
              throw new IllegalStateException("Name " + name + " already specified");
            }
            newRefs.put(name, value);
            renames.put(name, name);
          }
        });

    final Expr newExpression = mapRefs(expression, renames);
    return new TargetingExpr(newExpression, newRefs);
  }

  @Override
  public String toString() {
    return "TargetingExpr{" + "expression=" + expression + '}';
  }

  public static TargetingExpr materializedFromTargeting(
      final Targeting targeting, final Function<String, Targeting> segmentFetcher) {
    return materializedFromTargeting(targeting, segmentFetcher, new HashSet<>());
  }

  private static TargetingExpr materializedFromTargeting(
      final Targeting targeting,
      final Function<String, Targeting> segmentFetcher,
      final Set<String> visitedSegments) {
    final TargetingExpr expr = fromTargeting(targeting);
    final Map<String, Criterion> refs = new HashMap<>();
    final Map<String, Expr> segmentExprs = new HashMap<>();
    for (Map.Entry<String, Criterion> criterionEntry : expr.refs.entrySet()) {
      final String key = criterionEntry.getKey();

      final Criterion criterion = targeting.getCriteriaMap().get(key);
      if (criterion.hasAttribute()) {
        refs.put(key, criterion);
      } else if (criterion.hasSegment()) {
        final String segment = criterion.getSegment().getSegment();
        final Set<String> newVisited = new HashSet<>(visitedSegments);
        if (!newVisited.add(segment)) {
          throw new IllegalArgumentException("Recursion detected");
        }
        final TargetingExpr innerExpr =
            materializedFromTargeting(segmentFetcher.apply(segment), segmentFetcher, newVisited);
        for (var innerRef : innerExpr.refs.entrySet()) {
          refs.put(key + "." + innerRef.getKey(), innerRef.getValue());
        }
        segmentExprs.put(key, prefixRefsWith(key, innerExpr.expression));
      } else {
        throw new IllegalStateException();
      }
    }
    final Expr materialized = materializeSegments(expr.expression, segmentExprs);
    return new TargetingExpr(materialized, refs);
  }

  public static TargetingExpr fromTargeting(final Targeting targeting) {
    final Expr expr = ExprNormalizer.normalize(convert(targeting.getExpression()));
    final Set<String> includedRefs = new HashSet<>();
    collectRefs(expr, includedRefs);
    final Map<String, Criterion> refs = new HashMap<>(targeting.getCriteriaMap());
    for (String key : Set.copyOf(refs.keySet())) {
      if (!includedRefs.contains(key)) {
        refs.remove(key);
      }
    }

    return new TargetingExpr(expr, refs);
  }

  private static Expr prefixRefsWith(String prefix, Expr expr) {
    return switch (expr.type()) {
      case OR ->
          Expr.or(
              expr.operands().stream()
                  .map(inner -> prefixRefsWith(prefix, inner))
                  .collect(Collectors.toSet()));
      case AND ->
          Expr.and(
              expr.operands().stream()
                  .map(inner -> prefixRefsWith(prefix, inner))
                  .collect(Collectors.toSet()));
      case NOT -> Expr.not(prefixRefsWith(prefix, ((Not) expr).expr()));
      case FALSE, TRUE -> expr;
      case REF -> ref(prefix + "." + expr.name());
    };
  }

  private static Expr materializeSegments(Expr expr, Map<String, Expr> segmentExprs) {
    return switch (expr.type()) {
      case OR ->
          Expr.or(
              expr.operands().stream()
                  .map(inner -> materializeSegments(inner, segmentExprs))
                  .collect(Collectors.toSet()));
      case AND ->
          Expr.and(
              expr.operands().stream()
                  .map(inner -> materializeSegments(inner, segmentExprs))
                  .collect(Collectors.toSet()));
      case NOT -> Expr.not(materializeSegments(((Not) expr).expr(), segmentExprs));
      case FALSE, TRUE -> expr;
      case REF -> {
        final String key = expr.name();
        if (segmentExprs.containsKey(key)) {
          yield segmentExprs.get(key);
        } else {
          yield expr;
        }
      }
    };
  }

  public static Expr convert(final Expression expression) {
    return switch (expression.getExpressionCase()) {
      case REF -> ref(expression.getRef());
      case NOT -> not(convert(expression.getNot()));
      case AND ->
          and(
              expression.getAnd().getOperandsList().stream()
                  .map(TargetingExpr::convert)
                  .collect(toList()));
      case OR ->
          or(
              expression.getOr().getOperandsList().stream()
                  .map(TargetingExpr::convert)
                  .collect(toList()));
      default -> T;
    };
  }

  private static void collectRefs(final Expr expression, final Set<String> refs) {
    switch (expression.type()) {
      case REF -> refs.add(expression.name());
      case NOT -> collectRefs(expression.operands().iterator().next(), refs);
      case AND, OR -> {
        expression.operands().forEach(e -> collectRefs(e, refs));
        expression.operands().forEach(e -> collectRefs(e, refs));
      }
    }
  }

  private static Expr mapRefs(final Expr expression, final Map<String, String> refMap) {
    return switch (expression.type()) {
      case REF -> ref(refMap.get(expression.name()));
      case NOT -> not(mapRefs(expression.operands().iterator().next(), refMap));
      case AND ->
          and(expression.operands().stream().map(e -> mapRefs(e, refMap)).collect(toList()));
      case OR -> or(expression.operands().stream().map(e -> mapRefs(e, refMap)).collect(toList()));
      default -> expression;
    };
  }

  private static Map<String, Criterion> mapCriterionRefs(
      final Map<String, Criterion> refs, final Map<String, String> refMap) {
    return refs.entrySet().stream()
        .collect(toMap(e -> refMap.get(e.getKey()), Map.Entry::getValue));
  }

  private static class RefMapper {

    final Set<String> allRefs;
    final Map<String, Integer> intersectionCounters;

    private RefMapper(Set<String> allRefs, Map<String, Integer> intersectionCounters) {
      this.allRefs = allRefs;
      this.intersectionCounters = intersectionCounters;
    }

    static RefMapper create(Set<String> refs1, Set<String> refs2) {
      // intersect refs of both
      // map refs with id if not in intersection, or suffixed if in intersection

      final Set<String> allRefs = union(refs1, refs2);
      final Map<String, Integer> intersectionCounters =
          intersection(refs1, refs2).stream().collect(Collectors.toMap(identity(), ignore -> 0));

      return new RefMapper(allRefs, intersectionCounters);
    }

    private static <T> Set<T> union(Set<T> s1, Set<T> s2) {
      final Set<T> result = new HashSet<>(s1);
      result.addAll(s2);
      return result;
    }

    private static <T> Set<T> intersection(Set<T> s1, Set<T> s2) {
      final Set<T> result = new HashSet<>(s1);
      result.retainAll(s2);
      return result;
    }

    private String getNextNameFor(String ref) {
      // must only be called for names in the overlap
      final int count = requireNonNull(intersectionCounters.get(ref));
      intersectionCounters.put(ref, count + 1);
      return ref + "_" + count;
    }

    String mapRef(String ref) {
      String newRef;
      if (intersectionCounters.containsKey(ref)) {
        newRef = getNextNameFor(ref);
        while (allRefs.contains(newRef)) {
          newRef = getNextNameFor(ref);
        }
      } else {
        newRef = ref;
      }

      return newRef;
    }
  }

  public record CriterionEntry(String name, Criterion criterion) {}
}
