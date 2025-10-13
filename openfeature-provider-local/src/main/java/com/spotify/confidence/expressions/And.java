package com.spotify.confidence.expressions;

import static java.util.stream.Collectors.toSet;

import java.util.Set;
import java.util.stream.Stream;

record And(Set<Expr> operands) implements AndOr {

  @Override
  public Type type() {
    return Type.AND;
  }

  @Override
  public Expr simplify() {
    final Set<Expr> reduced =
        reduceNegatedPairsTo(Expr.F)
            .filter(o -> !o.isTrue())
            .flatMap(o -> o.isAnd() ? o.operands().stream() : Stream.of(o))
            .collect(toSet());

    if (reduced.contains(Expr.F)) {
      return Expr.F;
    } else if (reduced.size() == 1) {
      return reduced.iterator().next();
    } else if (reduced.isEmpty()) {
      return Expr.T;
    }
    return Expr.and(reduced);
  }

  @Override
  public String toString() {
    return name();
  }
}
