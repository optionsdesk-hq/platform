package com.optionsdesk.shared.domain;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The legs of one position, priced as a whole: mark-to-market P&L and the payoff at expiry.
 *
 * @param legs one or more legs
 * @param definedRisk true when every short leg is covered by a long one (the most it can lose is
 *     capped)
 */
public record Structure(List<Leg> legs, boolean definedRisk) {

  public Structure {
    Objects.requireNonNull(legs, "legs");
    if (legs.isEmpty()) {
      throw new IllegalArgumentException("a structure needs at least one leg");
    }
    legs = List.copyOf(legs);
  }

  /**
   * P&L in ₹ if every leg were closed at {@code prices} (by trading symbol). Every leg must be
   * priced.
   */
  public double markPnl(Map<String, Double> prices) {
    return legs.stream()
        .mapToDouble(
            l -> {
              Double price = prices.get(l.contract().tradingSymbol());
              if (price == null) {
                throw new IllegalArgumentException("no price for " + l.contract().tradingSymbol());
              }
              return l.markToMarket(price);
            })
        .sum();
  }

  /** P&L in ₹ at expiry with the index at {@code spot}. */
  public double payoffAt(double spot) {
    return legs.stream().mapToDouble(l -> l.payoffAtExpiry(spot)).sum();
  }

  /** Net premium received at entry in ₹ (negative = a debit paid). */
  public double credit() {
    return -legs.stream().mapToDouble(l -> l.entryPrice() * l.quantity()).sum();
  }

  /**
   * The widest distance from a short strike to a long strike of the same right, when every right
   * that has a short leg also has a long one (a fully covered spread); empty otherwise.
   */
  public java.util.OptionalDouble spreadWidth() {
    var shortedRights =
        legs.stream().filter(Leg::isShort).map(l -> l.contract().right()).distinct().toList();
    var coveredRights =
        legs.stream().filter(l -> !l.isShort()).map(l -> l.contract().right()).distinct().toList();
    if (!coveredRights.containsAll(shortedRights)) {
      return java.util.OptionalDouble.empty();
    }
    return legs.stream()
        .filter(Leg::isShort)
        .flatMapToDouble(
            s ->
                legs.stream()
                    .filter(l -> !l.isShort() && l.contract().right() == s.contract().right())
                    .mapToDouble(l -> Math.abs(s.contract().strike() - l.contract().strike())))
        .max();
  }

  /** True when every leg expires on the same day (calendars and diagonals don't). */
  public boolean singleExpiry() {
    return legs.stream().map(l -> l.contract().expiry()).distinct().count() == 1;
  }

  /**
   * Lowest and highest expiry payoff in ₹. The payoff is piecewise linear in the index with kinks
   * only at the strikes, so its extremes lie at a strike, at 0, or far above the top strike.
   */
  public double[] payoffRange() {
    double top = legs.stream().mapToDouble(l -> l.contract().strike()).max().orElseThrow();
    double low = payoffAt(0);
    double high = low;
    for (double spot : legs.stream().mapToDouble(l -> l.contract().strike()).distinct().toArray()) {
      double v = payoffAt(spot);
      low = Math.min(low, v);
      high = Math.max(high, v);
    }
    double far = payoffAt(top * 3);
    return new double[] {Math.min(low, far), Math.max(high, far)};
  }
}
