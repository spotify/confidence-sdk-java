package com.spotify.confidence.expressions;

import java.util.Set;

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
