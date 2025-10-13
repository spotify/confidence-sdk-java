package com.spotify.confidence.expressions;

import java.util.Set;

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
