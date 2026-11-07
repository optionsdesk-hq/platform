package com.optionsdesk.trading.risk.service;

import com.optionsdesk.shared.domain.Structure;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Sanity check on marks: a position is only valued, and only exits, on prices that can be true. A
 * bad print, a missing quote (price 0) or a stale far wing can make a mark impossible; in the
 * legacy system one booked a false +₹7,262 (PRE-38). Ported from {@code optionsdesk.risk.marks} and
 * checked against it (golden tests).
 *
 * <p>Two checks: every leg has a price above zero; and, for a single-expiry structure, the mark P&L
 * lies within what the structure can be worth (its lowest and highest expiry payoff) plus a small
 * tolerance for time value and mid prices. Undefined-risk positions have no floor, only the ceiling
 * (their maximum profit).
 */
public final class MarkSanity {

  /** Tolerance in index points per unit of the largest leg. */
  public static final double TOLERANCE_POINTS = 2.0;

  /** Why a mark was rejected. */
  public enum Kind {
    MISSING_PRICE,
    ABOVE_MAX_PROFIT,
    BELOW_MAX_LOSS
  }

  /** A rejected mark: the kind, and a sentence a human can act on. */
  public record Problem(Kind kind, String message) {}

  private MarkSanity() {}

  /** Empty when {@code prices} give a believable mark for {@code structure}; otherwise why not. */
  public static Optional<Problem> check(Structure structure, Map<String, Double> prices) {
    List<String> missing =
        structure.legs().stream()
            .map(l -> l.contract().tradingSymbol())
            .filter(s -> prices.get(s) == null || prices.get(s) <= 0)
            .toList();
    if (!missing.isEmpty()) {
      return Optional.of(
          new Problem(Kind.MISSING_PRICE, "no price for " + String.join(", ", missing)));
    }
    if (!structure.singleExpiry()) {
      return Optional.empty(); // calendars and diagonals: no simple expiry bound
    }
    double pnl = structure.markPnl(prices);
    double[] range = structure.payoffRange();
    int largest = structure.legs().stream().mapToInt(l -> Math.abs(l.quantity())).max().orElse(1);
    double tolerance = TOLERANCE_POINTS * largest;
    if (pnl > range[1] + tolerance) {
      return Optional.of(
          new Problem(
              Kind.ABOVE_MAX_PROFIT,
              "mark P&L ₹%,.0f is above the most this position can make (₹%,.0f)"
                  .formatted(pnl, range[1])));
    }
    if (structure.definedRisk() && pnl < range[0] - tolerance) {
      return Optional.of(
          new Problem(
              Kind.BELOW_MAX_LOSS,
              "mark P&L ₹%,.0f is below the most this position can lose (₹%,.0f)"
                  .formatted(pnl, range[0])));
    }
    return Optional.empty();
  }
}
