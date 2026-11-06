package com.optionsdesk.shared.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;

/**
 * Expiry payoff analytics for a structure, scanned over the index ±35% around spot (4,000 steps).
 * Ported from the legacy Python {@code strategies.payoff.payoff_stats} with the same grid, so
 * results match exactly.
 *
 * @param credit net premium received at entry in ₹
 * @param maxProfit best expiry P&L in the scanned range (₹)
 * @param maxLoss worst expiry P&L in the scanned range (₹, ≤ 0 for a credit structure)
 * @param breakevens index levels where the expiry P&L crosses zero (rounded to 0.1)
 * @param creditToWidth credit per unit ÷ spread width, for fully covered spreads only
 */
public record PayoffStats(
    double credit,
    double maxProfit,
    double maxLoss,
    List<Double> breakevens,
    OptionalDouble creditToWidth) {

  static final double SPAN = 0.35;
  static final int STEPS = 4000;

  public PayoffStats {
    breakevens = List.copyOf(breakevens);
  }

  public static PayoffStats of(Structure structure, double spot) {
    double lo = spot * (1 - SPAN);
    double hi = spot * (1 + SPAN);
    double[] xs = new double[STEPS + 1];
    double[] ys = new double[STEPS + 1];
    double max = Double.NEGATIVE_INFINITY;
    double min = Double.POSITIVE_INFINITY;
    for (int i = 0; i <= STEPS; i++) {
      xs[i] = lo + (hi - lo) * i / STEPS;
      ys[i] = structure.payoffAt(xs[i]);
      max = Math.max(max, ys[i]);
      min = Math.min(min, ys[i]);
    }
    List<Double> breakevens = new ArrayList<>();
    for (int i = 0; i < STEPS; i++) {
      double y0 = ys[i];
      double y1 = ys[i + 1];
      if ((y0 < 0 && 0 <= y1) || (y0 >= 0 && 0 > y1)) {
        double x = xs[i] + (xs[i + 1] - xs[i]) * (-y0) / (y1 - y0);
        breakevens.add(new BigDecimal(x).setScale(1, RoundingMode.HALF_EVEN).doubleValue());
      }
    }
    OptionalDouble ctw = OptionalDouble.empty();
    var width = structure.spreadWidth();
    var firstShort = structure.legs().stream().filter(Leg::isShort).findFirst();
    if (width.isPresent() && width.getAsDouble() > 0 && firstShort.isPresent()) {
      double shortUnits = Math.abs(firstShort.get().quantity());
      ctw = OptionalDouble.of(structure.credit() / shortUnits / width.getAsDouble());
    }
    return new PayoffStats(structure.credit(), max, min, breakevens, ctw);
  }
}
