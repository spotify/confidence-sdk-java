package com.spotify.confidence.expressions;

import static java.util.Collections.unmodifiableSet;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toCollection;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/* Expr ADT written with Java 15 sealed interfaces and records */
public sealed interface Expr extends Comparable<Expr> permits AndOr, False, Not, Ref, True {

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
