package com.optionsdesk.trading.strategy.model;

import com.optionsdesk.shared.domain.OptionContract;
import com.optionsdesk.shared.domain.Right;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.OptionalDouble;

/**
 * One expiry's option chain as strategies see it: each contract with its mid price and delta (delta
 * from the pricing module's implied volatility; empty when the price couldn't be solved).
 *
 * @param spot index level when the chain was taken
 * @param expiry the chain's expiry
 * @param quotes every contract quoted
 */
public record OptionChain(double spot, LocalDate expiry, List<Quote> quotes) {

  /** A quoted contract. */
  public record Quote(OptionContract contract, double mid, OptionalDouble delta) {
    public double strike() {
      return contract.strike();
    }

    public Right right() {
      return contract.right();
    }
  }

  public OptionChain {
    quotes = List.copyOf(quotes);
  }

  /**
   * The out-of-the-money option of {@code right} whose |delta| is closest to {@code target} (ties:
   * the lower strike). Only quotes with a price and a delta count.
   */
  public Quote byDelta(Right right, double target) {
    Quote best = null;
    double bestDistance = Double.POSITIVE_INFINITY;
    for (Quote q : sideSortedByStrike(right)) {
      boolean otm = right == Right.CE ? q.strike() > spot : q.strike() < spot;
      if (!otm || q.delta().isEmpty() || q.mid() <= 0) {
        continue;
      }
      double distance = Math.abs(Math.abs(q.delta().getAsDouble()) - target);
      if (distance
          < bestDistance) { // strict: the first (lowest strike) wins a tie, as in the legacy code
        best = q;
        bestDistance = distance;
      }
    }
    if (best == null) {
      throw new IllegalStateException(
          "no OTM " + right + " with a valid delta in the " + expiry + " chain");
    }
    return best;
  }

  /** The tradable quote at exactly {@code strike}, or an error naming what's missing. */
  public Quote require(double strike, Right right) {
    return quotes.stream()
        .filter(q -> q.right() == right && q.strike() == strike && q.mid() > 0)
        .findFirst()
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "no tradable %s at %s for %s".formatted(right, strike, expiry)));
  }

  private List<Quote> sideSortedByStrike(Right right) {
    return quotes.stream()
        .filter(q -> q.right() == right)
        .sorted(Comparator.comparingDouble(Quote::strike))
        .toList();
  }
}
