package com.spotify.confidence.targeting;

import static java.util.Collections.unmodifiableSet;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toCollection;
import static java.util.stream.Collectors.toSet;
import static java.util.stream.Stream.concat;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

/* Expr ADT written with Java 15 sealed interfaces and records */
public sealed interface Expr extends Comparable<Expr> {

  Expr T = new True();
  Expr F = new False();

  // this also defines the sort order of operands
  enum Type {
    TRUE,
    FALSE,
    REF,
    NOT,
    AND,
    OR
  }

  Type type();

  String name();

  Set<Expr> operands();

  static Expr ref(String name) {
    return new Ref(name);
  }

  static Expr not(Expr expr) {
    return new Not(expr);
  }

  static Expr and(Expr... operands) {
    return and(List.of(operands));
  }

  static Expr and(Collection<Expr> operands) {
    return new And(sort(operands));
  }

  static Expr or(Expr... operands) {
    return or(List.of(operands));
  }

  static Expr or(Collection<Expr> operands) {
    return new Or(sort(operands));
  }

  default Set<Expr> operandsOfType(Type type) {
    return operands().stream().filter(o -> o.type() == type).collect(toCollection(TreeSet::new));
  }

  default Set<Expr> operandsExcludingType(Type type) {
    return operands().stream().filter(o -> o.type() != type).collect(toCollection(TreeSet::new));
  }

  default boolean isTrue() {
    return this instanceof True;
  }

  default boolean isFalse() {
    return this instanceof False;
  }

  default boolean isRef() {
    return this instanceof Ref;
  }

  default boolean isNot() {
    return this instanceof Not;
  }

  default boolean isAnd() {
    return this instanceof And;
  }

  default boolean isOr() {
    return this instanceof Or;
  }

  default Expr simplify() {
    return this;
  }

  @Override
  default int compareTo(Expr other) {
    return Comparator.comparing(Expr::type).thenComparing(Expr::name).compare(this, other);
  }

  static Set<Expr> sort(Collection<Expr> operands) {
    return unmodifiableSet(new TreeSet<>(requireNonNull(operands)));
  }
}

record True() implements Expr {

  @Override
  public Type type() {
    return Type.TRUE;
  }

  @Override
  public String name() {
    return "T";
  }

  @Override
  public Set<Expr> operands() {
    return Set.of();
  }

  @Override
  public String toString() {
    return name();
  }
}

record False() implements Expr {

  @Override
  public Type type() {
    return Type.FALSE;
  }

  @Override
  public String name() {
    return "F";
  }

  @Override
  public Set<Expr> operands() {
    return Set.of();
  }

  @Override
  public String toString() {
    return name();
  }
}

record Ref(String name) implements Expr {

  @Override
  public Type type() {
    return Type.REF;
  }

  @Override
  public Set<Expr> operands() {
    return Set.of();
  }

  @Override
  public String toString() {
    return name();
  }
}

record Not(Expr expr) implements Expr {

  @Override
  public Type type() {
    return Type.NOT;
  }

  @Override
  public Expr simplify() {
    final Expr operand = operand().simplify();

    if (operand.isNot()) {
      // !!a -> a
      return operand.operands().iterator().next();
    } else if (operand.isTrue() || operand.isFalse()) {
      return operand.isTrue() ? F : T;
    }

    return Expr.not(operand);
  }

  @Override
  public String name() {
    if (operand().isAnd()) {
      return "!(" + expr().name() + ")";
    }
    return "!" + expr().name();
  }

  @Override
  public Set<Expr> operands() {
    return Set.of(expr());
  }

  public Expr operand() {
    return operands().iterator().next();
  }

  @Override
  public String toString() {
    return name();
  }
}

sealed interface AndOr extends Expr {

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

record And(Set<Expr> operands) implements AndOr {

  @Override
  public Type type() {
    return Type.AND;
  }

  @Override
  public Expr simplify() {
    final Set<Expr> reduced =
        reduceNegatedPairsTo(F)
            .filter(o -> !o.isTrue())
            .flatMap(o -> o.isAnd() ? o.operands().stream() : Stream.of(o))
            .collect(toSet());

    if (reduced.contains(F)) {
      return F;
    } else if (reduced.size() == 1) {
      return reduced.iterator().next();
    } else if (reduced.isEmpty()) {
      return T;
    }
    return Expr.and(reduced);
  }

  @Override
  public String toString() {
    return name();
  }
}

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
