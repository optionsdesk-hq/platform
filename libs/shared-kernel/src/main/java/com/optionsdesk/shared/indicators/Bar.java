package com.optionsdesk.shared.indicators;

/** One price bar (any timeframe): what the indicators read. */
public record Bar(double high, double low, double close) {

  /** True range against the previous bar's close (Wilder). */
  double trueRange(double previousClose) {
    return Math.max(
        high - low, Math.max(Math.abs(high - previousClose), Math.abs(low - previousClose)));
  }
}
