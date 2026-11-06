package com.optionsdesk.shared.domain;

/** An option's right: a call (CE) or a put (PE), named as NSE does. */
public enum Right {
  CE,
  PE;

  /** Intrinsic value of one unit at {@code spot} for a strike of {@code strike}. */
  public double intrinsic(double spot, double strike) {
    return this == CE ? Math.max(spot - strike, 0.0) : Math.max(strike - spot, 0.0);
  }
}
