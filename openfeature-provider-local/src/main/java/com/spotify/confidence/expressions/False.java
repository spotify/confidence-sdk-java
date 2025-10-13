package com.spotify.confidence.expressions;

import java.util.Set;

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
