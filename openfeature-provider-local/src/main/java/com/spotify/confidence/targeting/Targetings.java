package com.spotify.confidence.targeting;

import com.google.protobuf.Timestamp;
import com.google.protobuf.util.Timestamps;
import com.spotify.confidence.shaded.flags.types.v1.Expression;
import com.spotify.confidence.shaded.flags.types.v1.Targeting;
import com.spotify.confidence.shaded.flags.types.v1.Targeting.Criterion;
import com.spotify.confidence.shaded.flags.types.v1.Targeting.Criterion.AttributeCriterion;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

public class Targetings {

  public static Expression ref(final String ref) {
    return Expression.newBuilder().setRef(ref).build();
  }

  public static Expression not(final Expression expression) {
    return Expression.newBuilder().setNot(expression).build();
  }

  public static Expression and(final Expression... expressions) {
    return and(List.of(expressions));
  }

  public static Expression and(final Collection<Expression> expressions) {
    return Expression.newBuilder()
        .setAnd(Expression.Operands.newBuilder().addAllOperands(expressions).build())
        .build();
  }

  public static Expression or(final Expression... expressions) {
    return or(List.of(expressions));
  }

  public static Expression or(final Collection<Expression> expressions) {
    return Expression.newBuilder()
        .setOr(Expression.Operands.newBuilder().addAllOperands(expressions).build())
        .build();
  }

  public static Targeting.EqRule eqRule(final Targeting.Value value) {
    return Targeting.EqRule.newBuilder().setValue(value).build();
  }

  public static Targeting.SetRule setRule(final Targeting.Value... values) {
    return Targeting.SetRule.newBuilder().addAllValues(List.of(values)).build();
  }

  public static Targeting.RangeRule gtRule(final Targeting.Value value) {
    return Targeting.RangeRule.newBuilder().setStartExclusive(value).build();
  }

  public static Targeting.RangeRule gteRule(final Targeting.Value value) {
    return Targeting.RangeRule.newBuilder().setStartInclusive(value).build();
  }

  public static Targeting.RangeRule ltRule(final Targeting.Value value) {
    return Targeting.RangeRule.newBuilder().setEndExclusive(value).build();
  }

  public static Targeting.RangeRule lteRule(final Targeting.Value value) {
    return Targeting.RangeRule.newBuilder().setEndInclusive(value).build();
  }

  public static Targeting.RangeRule gtltRule(final Targeting.Value gt, final Targeting.Value lt) {
    return Targeting.RangeRule.newBuilder().setStartExclusive(gt).setEndExclusive(lt).build();
  }

  public static Targeting.RangeRule gtlteRule(final Targeting.Value gt, final Targeting.Value lte) {
    return Targeting.RangeRule.newBuilder().setStartExclusive(gt).setEndInclusive(lte).build();
  }

  public static Targeting.RangeRule gteltRule(final Targeting.Value gte, final Targeting.Value lt) {
    return Targeting.RangeRule.newBuilder().setStartInclusive(gte).setEndExclusive(lt).build();
  }

  public static Targeting.RangeRule gtelteRule(
      final Targeting.Value gte, final Targeting.Value lte) {
    return Targeting.RangeRule.newBuilder().setStartInclusive(gte).setEndInclusive(lte).build();
  }

  public static Targeting.Value boolValue(final boolean value) {
    return Targeting.Value.newBuilder().setBoolValue(value).build();
  }

  public static Targeting.Value numberValue(final double value) {
    return Targeting.Value.newBuilder().setNumberValue(value).build();
  }

  public static Targeting.Value stringValue(final String value) {
    return Targeting.Value.newBuilder().setStringValue(value).build();
  }

  public static Targeting.Value timestampValue(final long millis) {
    return Targeting.Value.newBuilder().setTimestampValue(Timestamps.fromMillis(millis)).build();
  }

  public static Targeting.Value timestampValue(final Timestamp value) {
    return Targeting.Value.newBuilder().setTimestampValue(value).build();
  }

  public static Targeting.Value semverValue(final String value) {
    return Targeting.Value.newBuilder()
        .setVersionValue(Targeting.SemanticVersion.newBuilder().setVersion(value).build())
        .build();
  }

  public static Targeting.Criterion criterion(
      final String attributeName, final Consumer<AttributeCriterion.Builder> build) {
    final AttributeCriterion.Builder builder =
        AttributeCriterion.newBuilder().setAttributeName(attributeName);
    build.accept(builder);
    return Targeting.Criterion.newBuilder().setAttribute(builder.build()).build();
  }

  public static Targeting.Criterion segmentCriterion(final String segment) {
    return Targeting.Criterion.newBuilder()
        .setSegment(Criterion.SegmentCriterion.newBuilder().setSegment(segment).build())
        .build();
  }
}
