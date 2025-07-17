package com.spotify.confidence;

import static com.spotify.confidence.Expr.T;
import static com.spotify.confidence.Expr.and;
import static com.spotify.confidence.Expr.not;
import static com.spotify.confidence.Expr.or;
import static com.spotify.confidence.Expr.ref;
import static com.spotify.confidence.Util.evalExpression;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;

import com.spotify.confidence.shaded.flags.types.v1.Expression;
import com.spotify.confidence.shaded.flags.types.v1.Targeting;
import com.spotify.confidence.shaded.flags.types.v1.Targeting.Criterion;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

class TargetingExpr {

  private final Expr expression;
  private final Map<String, Criterion> refs;

  TargetingExpr(final Expr expression, final Map<String, Criterion> refs) {
    // todo: check invariants
    // . all refs in expression exist as keys in the refs map

    this.expression = requireNonNull(expression);
    this.refs = requireNonNull(refs);
  }

  Map<String, Criterion> refs() {
    return refs;
  }

  boolean eval(Set<String> trueRefs) {
    return evalExpression(expression, trueRefs);
  }

  @Override
  public String toString() {
    return "TargetingExpr{" + "expression=" + expression + '}';
  }

  static TargetingExpr fromTargeting(final Targeting targeting) {
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

  static Expr convert(final Expression expression) {
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
}
