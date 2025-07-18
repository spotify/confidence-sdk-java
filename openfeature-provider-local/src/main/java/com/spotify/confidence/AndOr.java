package com.spotify.confidence;

import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toSet;
import static java.util.stream.Stream.concat;

import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

sealed interface AndOr extends Expr permits And, Or {

  @Override
  default String name() {
    return operands().stream()
        .map(Expr::name)
        .collect(joining(delimiter(), isOr() ? "(" : "", isOr() ? ")" : ""));
  }

  private String delimiter() {
    return type() == Type.AND ? "·" : " + ";
  }

  default Stream<Expr> reduceNegatedPairsTo(Expr substitute) {
    final List<Expr> negatedExpressions =
        operandsOfType(Type.NOT).stream()
            .map(not -> not.operands().iterator().next())
            .map(Expr::simplify)
            .toList();
    final Set<Expr> rest =
        operandsExcludingType(Type.NOT).stream().map(Expr::simplify).collect(toSet());

    final Stream<Expr> matched =
        rest.stream().map(o -> negatedExpressions.contains(o) ? substitute : o);

    final Stream<Expr> nonMatched =
        negatedExpressions.stream()
            .filter(o -> !rest.contains(o))
            .map(Expr::not) // wrap in not operator again;
            .map(Expr::simplify);

    return concat(matched, nonMatched);
  }
}
