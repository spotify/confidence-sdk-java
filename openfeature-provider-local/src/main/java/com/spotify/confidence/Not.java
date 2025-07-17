package com.spotify.confidence;

import java.util.Set;

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
