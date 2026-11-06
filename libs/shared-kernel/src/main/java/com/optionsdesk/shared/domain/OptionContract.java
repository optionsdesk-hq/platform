package com.optionsdesk.shared.domain;

import java.time.LocalDate;
import java.util.Objects;

/**
 * One listed option contract. A value object: two contracts with the same fields are the same
 * contract.
 *
 * @param tradingSymbol the exchange symbol, e.g. {@code NIFTY26N1024650CE}
 * @param underlying index name, e.g. {@code NIFTY}
 * @param expiry expiry date
 * @param strike strike price in index points
 * @param right call or put
 * @param lotSize units per lot (for example 65 for NIFTY)
 */
public record OptionContract(
    String tradingSymbol,
    String underlying,
    LocalDate expiry,
    double strike,
    Right right,
    int lotSize) {

  public OptionContract {
    Objects.requireNonNull(tradingSymbol, "tradingSymbol");
    Objects.requireNonNull(underlying, "underlying");
    Objects.requireNonNull(expiry, "expiry");
    Objects.requireNonNull(right, "right");
    if (strike <= 0) {
      throw new IllegalArgumentException("strike must be positive: " + strike);
    }
    if (lotSize <= 0) {
      throw new IllegalArgumentException("lotSize must be positive: " + lotSize);
    }
  }
}
