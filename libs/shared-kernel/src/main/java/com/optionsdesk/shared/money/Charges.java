package com.optionsdesk.shared.money;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Exchange and broker charges for options orders, and the paper-fill slippage model. Rates come
 * from the rulebook (Zerodha's published charges); ported from the legacy Python {@code
 * optionsdesk.execution.costs} and checked against it (golden tests).
 *
 * @param brokeragePerOrder flat ₹ per executed order
 * @param sttSellPctOfPremium securities transaction tax, % of premium, sell side only
 * @param exchangeTxnPctOfPremium exchange transaction charge, % of premium
 * @param sebiPerCrore SEBI turnover fee, ₹ per crore of premium
 * @param stampBuyPctOfPremium stamp duty, % of premium, buy side only
 * @param gstPct GST, % of (brokerage + exchange + SEBI)
 * @param slippageTicksPerLeg paper fills: ticks moved against us from the mid
 * @param tickSize price tick in ₹
 */
public record Charges(
    double brokeragePerOrder,
    double sttSellPctOfPremium,
    double exchangeTxnPctOfPremium,
    double sebiPerCrore,
    double stampBuyPctOfPremium,
    double gstPct,
    int slippageTicksPerLeg,
    double tickSize) {

  public Charges {
    if (brokeragePerOrder < 0
        || sttSellPctOfPremium < 0
        || exchangeTxnPctOfPremium < 0
        || sebiPerCrore < 0
        || stampBuyPctOfPremium < 0
        || gstPct < 0
        || slippageTicksPerLeg < 0
        || tickSize <= 0) {
      throw new IllegalArgumentException(
          "charge rates can't be negative; tick size must be positive");
    }
  }

  /** All charges in ₹ for one options order. {@code premiumTurnover} = price × quantity. */
  public double order(double premiumTurnover, boolean isSell) {
    double exchange = premiumTurnover * exchangeTxnPctOfPremium / 100;
    double sebi = premiumTurnover * sebiPerCrore / 1e7;
    double stt = isSell ? premiumTurnover * sttSellPctOfPremium / 100 : 0.0;
    double stamp = isSell ? 0.0 : premiumTurnover * stampBuyPctOfPremium / 100;
    double gst = (brokeragePerOrder + exchange + sebi) * gstPct / 100;
    return brokeragePerOrder + exchange + sebi + stt + stamp + gst;
  }

  /**
   * Paper fill: the mid moved against us by the configured ticks, rounded to paise, never below one
   * tick.
   */
  public double slippedPrice(double mid, boolean buying) {
    double adjustment = slippageTicksPerLeg * tickSize;
    double moved = buying ? mid + adjustment : mid - adjustment;
    double rounded = BigDecimal.valueOf(moved).setScale(2, RoundingMode.HALF_EVEN).doubleValue();
    return Math.max(rounded, tickSize);
  }
}
