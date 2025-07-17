package com.spotify.confidence;

import static java.util.stream.Collectors.toSet;

import java.util.Set;
import java.util.stream.Stream;

record Or(Set<Expr> operands) implements AndOr {

  @Override
  public Type type() {
    return Type.OR;
  }

  @Override
  public Expr simplify() {
    final Set<Expr> reduced =
        reduceNegatedPairsTo(T)
            .filter(o -> !o.isFalse())
            .flatMap(o -> o.isOr() ? o.operands().stream() : Stream.of(o))
            .collect(toSet());

    if (reduced.contains(T)) {
      return T;
    } else if (reduced.size() == 1) {
      return reduced.iterator().next();
    } else if (reduced.isEmpty()) {
      return F;
    }
    return Expr.or(reduced);
  }

  @Override
  public String toString() {
    return name();
  }
}
