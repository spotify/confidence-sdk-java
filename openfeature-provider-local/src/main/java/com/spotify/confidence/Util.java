package com.spotify.confidence;

import static com.spotify.confidence.shaded.flags.types.v1.Targeting.RangeRule.EndCase.END_EXCLUSIVE;
import static com.spotify.confidence.shaded.flags.types.v1.Targeting.RangeRule.EndCase.END_INCLUSIVE;
import static com.spotify.confidence.shaded.flags.types.v1.Targeting.RangeRule.EndCase.END_NOT_SET;
import static com.spotify.confidence.shaded.flags.types.v1.Targeting.RangeRule.StartCase.START_EXCLUSIVE;
import static com.spotify.confidence.shaded.flags.types.v1.Targeting.RangeRule.StartCase.START_INCLUSIVE;
import static com.spotify.confidence.shaded.flags.types.v1.Targeting.RangeRule.StartCase.START_NOT_SET;
import static java.util.stream.Collectors.toList;

import com.spotify.confidence.shaded.flags.types.v1.Targeting.EqRule;
import com.spotify.confidence.shaded.flags.types.v1.Targeting.RangeRule;
import com.spotify.confidence.shaded.flags.types.v1.Targeting.SetRule;
import com.spotify.confidence.shaded.flags.types.v1.Targeting.Value;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

final class Util {

  private Util() {}

  static boolean evalExpression(Expr expr, Set<String> trueRefs) {
    return switch (expr.type()) {
      case TRUE -> true;
      case FALSE -> false;
      case REF -> trueRefs.contains(expr.name());
      case NOT -> !evalExpression(expr.operands().iterator().next(), trueRefs);
      case AND -> expr.operands().stream().allMatch(op -> evalExpression(op, trueRefs));
      case OR -> expr.operands().stream().anyMatch(op -> evalExpression(op, trueRefs));
    };
  }

  // eq
  static boolean hasOverlap(EqRule r1, EqRule r2) {
    ensureSameType(r1.getValue(), r2.getValue());
    final Eq eq = Eq.getEq(r1.getValue().getValueCase());

    return eq.eq(r1.getValue(), r2.getValue());
  }

  // eq, contains
  static boolean hasOverlap(EqRule r1, SetRule r2) {
    r2.getValuesList().forEach(v -> ensureSameType(r1.getValue(), v));
    final Eq eq = Eq.getEq(r1.getValue().getValueCase());

    return eq.contains(r2.getValuesList(), r1.getValue());
  }

  static boolean hasOverlap(EqRule r1, RangeRule r2) {
    return hasOverlap(r1.getValue(), r2);
  }

  // lt, lte
  static boolean hasOverlap(Value value, RangeRule r2) {
    ensureSameType(value, r2);
    final Ord ord = Ord.getOrd(value);

    final boolean afterStart;
    final boolean beforeEnd;

    afterStart =
        switch (r2.getStartCase()) {
          case START_INCLUSIVE -> ord.lte(r2.getStartInclusive(), value);
          case START_EXCLUSIVE -> ord.lt(r2.getStartExclusive(), value);
          default -> false;
        };

    beforeEnd =
        switch (r2.getEndCase()) {
          case END_INCLUSIVE -> ord.lte(value, r2.getEndInclusive());
          case END_EXCLUSIVE -> ord.lt(value, r2.getEndExclusive());
          default -> false;
        };

    return (r2.getStartCase() == START_NOT_SET || afterStart)
        && (r2.getEndCase() == END_NOT_SET || beforeEnd);
  }

  // eq, intersect
  static boolean hasOverlap(SetRule r1, SetRule r2) {
    r1.getValuesList().forEach(v1 -> r2.getValuesList().forEach(v2 -> ensureSameType(v1, v2)));

    final Eq eq = Eq.getEq(r1.getValuesList().get(0).getValueCase());
    return eq.overlap(r1.getValuesList(), r2.getValuesList());
  }

  static boolean hasOverlapNeg(SetRule r1, SetRule r2Negated) {
    r1.getValuesList()
        .forEach(v1 -> r2Negated.getValuesList().forEach(v2 -> ensureSameType(v1, v2)));

    final Eq eq = Eq.getEq(r1.getValuesList().get(0).getValueCase());
    return eq.overlapNeg(r1.getValuesList(), r2Negated.getValuesList());
  }

  static boolean hasOverlap(SetRule r1, RangeRule r2) {
    return r1.getValuesList().stream().anyMatch(value -> hasOverlap(value, r2));
  }

  // lt, lte
  static boolean hasOverlap(RangeRule r1, RangeRule r2) {
    ensureSameType(r1, r2);

    if (r1.getStartCase() == START_NOT_SET && r1.getEndCase() == END_NOT_SET) {
      return true;
    }

    if (r2.getStartCase() == START_NOT_SET && r2.getEndCase() == END_NOT_SET) {
      return true;
    }

    final boolean x1y2E = r1.getStartCase() == START_EXCLUSIVE || r2.getEndCase() == END_EXCLUSIVE;
    final boolean y1x2E = r2.getStartCase() == START_EXCLUSIVE || r1.getEndCase() == END_EXCLUSIVE;

    final Value x1 =
        r1.getStartCase() == START_INCLUSIVE ? r1.getStartInclusive() : r1.getStartExclusive();
    final Value x2 = r1.getEndCase() == END_INCLUSIVE ? r1.getEndInclusive() : r1.getEndExclusive();
    final Value y1 =
        r2.getStartCase() == START_INCLUSIVE ? r2.getStartInclusive() : r2.getStartExclusive();
    final Value y2 = r2.getEndCase() == END_INCLUSIVE ? r2.getEndInclusive() : r2.getEndExclusive();

    // .....]
    //    [....]
    // or
    // [....]
    //    [.....
    if (r1.getStartCase() == START_NOT_SET && r2.getStartCase() != START_NOT_SET
        || r2.getEndCase() == END_NOT_SET && r1.getEndCase() != END_NOT_SET) {
      if (y1x2E) {
        return Ord.getOrd(y1).lt(y1, x2);
      } else {
        return Ord.getOrd(y1).lte(y1, x2);
      }
    }

    //     [.....
    // [....]
    // or
    //     [....]
    // .....]
    if (r1.getEndCase() == END_NOT_SET && r2.getEndCase() != END_NOT_SET
        || r2.getStartCase() == START_NOT_SET && r1.getStartCase() != START_NOT_SET) {
      if (x1y2E) {
        return Ord.getOrd(x1).lt(x1, y2);
      } else {
        return Ord.getOrd(x1).lte(x1, y2);
      }
    }

    final Ord ord = Ord.getOrd(x1);
    if (x1y2E && y1x2E) {
      return ord.lt(x1, y2) && ord.lt(y1, x2);
    } else if (x1y2E) {
      return ord.lt(x1, y2) && ord.lte(y1, x2);
    } else if (y1x2E) {
      return ord.lte(x1, y2) && ord.lt(y1, x2);
    } else {
      return ord.lte(x1, y2) && ord.lte(y1, x2);
    }
  }

  static SetRule intersectSets(SetRule set1, SetRule set2) {
    final List<Value> values1 = set1.getValuesList();
    final List<Value> values2 = set2.getValuesList();
    values1.forEach(v1 -> values2.forEach(v2 -> ensureSameType(v1, v2)));

    if (values1.isEmpty() || values2.isEmpty()) {
      return SetRule.getDefaultInstance();
    }

    final Eq eq = Eq.getEq(values1.get(0).getValueCase());
    final List<Value> intersection =
        values1.stream().filter(v -> eq.contains(values2, v)).collect(toList());
    return SetRule.newBuilder().addAllValues(intersection).build();
  }

  static SetRule unionSets(SetRule set1, SetRule set2) {
    final List<Value> values1 = set1.getValuesList();
    final List<Value> values2 = set2.getValuesList();
    values1.forEach(v1 -> values2.forEach(v2 -> ensureSameType(v1, v2)));

    if (values1.isEmpty() && values2.isEmpty()) {
      return SetRule.getDefaultInstance();
    }

    if (values1.isEmpty()) {
      return set2;
    }

    if (values2.isEmpty()) {
      return set1;
    }

    final Eq eq = Eq.getEq(values1.get(0).getValueCase());
    final List<Value> filter = new ArrayList<>();
    final List<Value> union =
        Stream.concat(values1.stream(), values2.stream())
            .filter(
                v -> {
                  final boolean contained = eq.contains(filter, v);
                  filter.add(v);
                  return !contained;
                })
            .collect(toList());
    return SetRule.newBuilder().addAllValues(union).build();
  }

  static SetRule subtractSet(SetRule set, SetRule subtract) {
    final List<Value> values1 = set.getValuesList();
    final List<Value> values2 = subtract.getValuesList();
    values1.forEach(v1 -> values2.forEach(v2 -> ensureSameType(v1, v2)));

    if (values1.isEmpty()) {
      return SetRule.getDefaultInstance();
    }

    final Eq eq = Eq.getEq(values1.get(0).getValueCase());
    final List<Value> subtracted =
        values1.stream().filter(v -> !eq.contains(values2, v)).collect(toList());
    return SetRule.newBuilder().addAllValues(subtracted).build();
  }

  static RangeRule intersectRanges(RangeRule range1, RangeRule range2) {
    ensureSameType(range1, range2);

    final RangeRule.Builder builder = RangeRule.newBuilder();

    // max of start values
    if (range1.getStartCase() != START_NOT_SET && range2.getStartCase() == START_NOT_SET) {
      if (range1.hasStartInclusive()) {
        builder.setStartInclusive(range1.getStartInclusive());
      } else if (range1.hasStartExclusive()) {
        builder.setStartExclusive(range1.getStartExclusive());
      }
    } else if (range1.getStartCase() == START_NOT_SET && range2.getStartCase() != START_NOT_SET) {
      if (range2.hasStartInclusive()) {
        builder.setStartInclusive(range2.getStartInclusive());
      } else if (range2.hasStartExclusive()) {
        builder.setStartExclusive(range2.getStartExclusive());
      }
    } else if (range1.getStartCase() != START_NOT_SET) { // both starts must be set
      final Value v1 =
          range1.hasStartInclusive() ? range1.getStartInclusive() : range1.getStartExclusive();
      final Value v2 =
          range2.hasStartInclusive() ? range2.getStartInclusive() : range2.getStartExclusive();

      final Ord ord = Ord.getOrd(v1);
      if (ord.lt(v1, v2)) {
        if (range2.hasStartInclusive()) {
          builder.setStartInclusive(v2);
        } else if (range2.hasStartExclusive()) {
          builder.setStartExclusive(v2);
        }
      } else if (ord.lt(v2, v1)) {
        if (range1.hasStartInclusive()) {
          builder.setStartInclusive(v1);
        } else if (range1.hasStartExclusive()) {
          builder.setStartExclusive(v1);
        }
      } else { // equal
        if (range1.hasStartExclusive() || range2.hasStartExclusive()) {
          builder.setStartExclusive(v1);
        } else {
          builder.setStartInclusive(v1);
        }
      }
    }

    // min of end values
    if (range1.getEndCase() != END_NOT_SET && range2.getEndCase() == END_NOT_SET) {
      if (range1.hasEndInclusive()) {
        builder.setEndInclusive(range1.getEndInclusive());
      } else if (range1.hasEndExclusive()) {
        builder.setEndExclusive(range1.getEndExclusive());
      }
    } else if (range1.getEndCase() == END_NOT_SET && range2.getEndCase() != END_NOT_SET) {
      if (range2.hasEndInclusive()) {
        builder.setEndInclusive(range2.getEndInclusive());
      } else if (range2.hasEndExclusive()) {
        builder.setEndExclusive(range2.getEndExclusive());
      }
    } else if (range1.getEndCase() != END_NOT_SET) { // both starts must be set
      final Value v1 =
          range1.hasEndInclusive() ? range1.getEndInclusive() : range1.getEndExclusive();
      final Value v2 =
          range2.hasEndInclusive() ? range2.getEndInclusive() : range2.getEndExclusive();

      final Ord ord = Ord.getOrd(v1);
      if (ord.lt(v1, v2)) {
        if (range1.hasEndInclusive()) {
          builder.setEndInclusive(v1);
        } else if (range1.hasEndExclusive()) {
          builder.setEndExclusive(v1);
        }
      } else if (ord.lt(v2, v1)) {
        if (range2.hasEndInclusive()) {
          builder.setEndInclusive(v2);
        } else if (range2.hasEndExclusive()) {
          builder.setEndExclusive(v2);
        }
      } else { // equal
        if (range1.hasEndExclusive() || range2.hasEndExclusive()) {
          builder.setEndExclusive(v1);
        } else {
          builder.setEndInclusive(v1);
        }
      }
    }

    return builder.build();
  }

  static SetRule filterSetByRange(SetRule set, RangeRule range) {
    final List<Value> values = set.getValuesList();
    values.forEach(v -> ensureSameType(v, range));
    // todo check all values are of same type in set

    final List<Value> filtered =
        values.stream().filter(v -> hasOverlap(v, range)).collect(toList());
    return SetRule.newBuilder().addAllValues(filtered).build();
  }

  static boolean emptyRange(RangeRule range) {
    if (range.getStartCase() != START_NOT_SET && range.getEndCase() != END_NOT_SET) {
      final Value startVal =
          range.hasStartInclusive() ? range.getStartInclusive() : range.getStartExclusive();
      final Value endVal =
          range.hasEndInclusive() ? range.getEndInclusive() : range.getEndExclusive();

      final Ord ord = Ord.getOrd(startVal);
      if (ord.lt(endVal, startVal)) {
        return true;
      } else if (ord.lt(startVal, endVal)) {
        return false;
      } else {
        return range.hasStartExclusive() || range.hasEndExclusive();
      }
    }
    return false;
  }

  private static void ensureSameType(RangeRule range1, RangeRule range2) {
    if (range1.getStartCase() == START_INCLUSIVE) {
      ensureSameType(range1.getStartInclusive(), range2);
    } else if (range1.getStartCase() == START_EXCLUSIVE) {
      ensureSameType(range1.getStartExclusive(), range2);
    }

    if (range1.getEndCase() == END_INCLUSIVE) {
      ensureSameType(range1.getEndInclusive(), range2);
    } else if (range1.getEndCase() == END_EXCLUSIVE) {
      ensureSameType(range1.getEndExclusive(), range2);
    }
  }

  private static void ensureSameType(Value value, RangeRule range) {
    if (range.getStartCase() == START_INCLUSIVE) {
      ensureSameType(value, range.getStartInclusive());
    } else if (range.getStartCase() == START_EXCLUSIVE) {
      ensureSameType(value, range.getStartExclusive());
    }

    if (range.getEndCase() == END_INCLUSIVE) {
      ensureSameType(value, range.getEndInclusive());
    } else if (range.getEndCase() == END_EXCLUSIVE) {
      ensureSameType(value, range.getEndExclusive());
    }
  }

  private static void ensureSameType(Value v1, Value v2) {
    if (v1.getValueCase() != v2.getValueCase()) {
      throw new IllegalArgumentException(
          "Non-matching types " + v1.getValueCase() + " != " + v2.getValueCase());
    }
  }
}
