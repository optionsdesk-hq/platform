package com.optionsdesk.shared.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.OptionalDouble;

/**
 * A price snapshot for one contract. Bid or ask may be missing (0) when that side of the book is
 * empty.
 *
 * @param contract the contract quoted
 * @param ltp last traded price
 * @param bid best bid, 0 if none
 * @param ask best ask, 0 if none
 * @param at when the quote was taken
 */
public record Quote(OptionContract contract, double ltp, double bid, double ask, Instant at) {

  public Quote {
    Objects.requireNonNull(contract, "contract");
    Objects.requireNonNull(at, "at");
    if (ltp < 0 || bid < 0 || ask < 0) {
      throw new IllegalArgumentException("prices can't be negative");
    }
  }

  /** Mid of bid and ask when both sides exist, otherwise the last traded price if there is one. */
  public OptionalDouble mid() {
    if (bid > 0 && ask > 0) {
      return OptionalDouble.of((bid + ask) / 2);
    }
    return ltp > 0 ? OptionalDouble.of(ltp) : OptionalDouble.empty();
  }

  /** Bid–ask spread in points, or empty when a side is missing. */
  public OptionalDouble spread() {
    return bid > 0 && ask > 0 ? OptionalDouble.of(ask - bid) : OptionalDouble.empty();
  }

  /** True when the quote is older than {@code maxAge} at {@code now}. */
  public boolean isStale(Instant now, Duration maxAge) {
    return Duration.between(at, now).compareTo(maxAge) > 0;
  }
}
