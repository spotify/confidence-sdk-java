package com.spotify.confidence.expressions;

import static com.spotify.confidence.expressions.Expr.and;
import static com.spotify.confidence.expressions.Expr.not;
import static com.spotify.confidence.expressions.Expr.or;
import static java.util.stream.Collectors.toList;

import com.spotify.confidence.expressions.Expr.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Normalizes arbitrary {@link Expr} instances into a Sum of Products form. */
class ExprNormalizer {

  static Expr normalize(final Expr expression) {
    final Expr simplified = expression.simplify();
    return normalizeInternal(simplified).simplify();
  }

  private static Expr normalizeInternal(final Expr expression) {
    return switch (expression.type()) {
      case NOT -> normalizeNot(expression.operands().iterator().next());
      case AND -> normalizeAnd(expression.operands());
      case OR -> normalizeOr(expression.operands());
      case TRUE, FALSE, REF -> expression;
    };
  }

  // pushes down negations further down the expression tree
  private static Expr normalizeNot(final Expr operand) {
    return switch (operand.type()) {
      case AND ->
          // !(a*b*...*x) -> !a+!b+...+!x
          normalize(
              or(
                  operand.operands().stream()
                      .map(Expr::not)
                      .map(ExprNormalizer::normalize)
                      .collect(toList())));
      case OR ->
          // !(a+b+...+x) -> !a*!b*...*!x
          normalize(
              and(
                  operand.operands().stream()
                      .map(Expr::not)
                      .map(ExprNormalizer::normalize)
                      .collect(toList())));
      default -> not(operand);
    };
  }

  // pulls up sums further up in the expression tree
  private static Expr normalizeAnd(final Set<Expr> and) {
    final Expr normalized = and(and.stream().map(ExprNormalizer::normalize).collect(toList()));

    // (a+b+c...) (d+e+f...) ...
    final List<Expr> ors =
        normalized.operands().stream().filter(operand -> operand.type() == Type.OR).toList();

    // start with a single term of all factors: everything that is not an or
    List<List<Expr>> terms =
        List.of(
            normalized.operands().stream()
                .filter(operand -> operand.type() != Type.OR)
                .collect(toList()));

    // apply distributive law over all 'or' expressions
    List<List<Expr>> newSum = new ArrayList<>();
    for (Expr or : ors) {
      for (List<Expr> term : terms) {
        for (Expr orTerm : or.operands()) {
          final List<Expr> product = new ArrayList<>(term);
          product.add(orTerm);
          newSum.add(product);
        }
      }
      terms = newSum;
      newSum = new ArrayList<>();
    }

    // single term does not need a sum
    if (terms.size() == 1) {
      final List<Expr> expressions = terms.get(0);
      if (expressions.size() == 1) {
        return expressions.get(0);
      }
      return and(expressions);
    }
    // sum of products
    return normalize(or(terms.stream().map(Expr::and).collect(toList())));
  }

  private static Expr normalizeOr(final Set<Expr> or) {
    return or(or.stream().map(ExprNormalizer::normalize).collect(toList()));
  }
}
