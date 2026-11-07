package com.optionsdesk.trading.risk.service;

import com.optionsdesk.trading.risk.model.StrategyLimits;
import java.util.Optional;

/**
 * Exit rules for an open position: profit target, loss stop as a multiple of the credit, time exit;
 * and the portfolio's daily stop. Ported from the legacy Python {@code risk/exits.py} (same order
 * of precedence, golden-tested).
 */
public final class Exits {

  /** Why a position (or the whole book) must close now. */
  public record ExitSignal(String reason, String detail) {}

  private Exits() {}

  /**
   * The exit due for a position with entry {@code credit} (₹), current {@code pnl} (₹) and {@code
   * dte} days to expiry, or empty. The profit target is checked first, then the stop, then time.
   */
  public static Optional<ExitSignal> forPosition(
      double credit, double pnl, int dte, StrategyLimits limits) {
    if (credit > 0 && pnl >= credit * limits.profitTargetPct() / 100) {
      return Optional.of(
          new ExitSignal(
              "profit_target",
              "P&L ₹%,.0f ≥ %s%% of credit ₹%,.0f"
                  .formatted(pnl, limits.profitTargetPct(), credit)));
    }
    if (credit > 0 && pnl <= -credit * limits.stopLossXCredit()) {
      return Optional.of(
          new ExitSignal(
              "stop_loss", "P&L ₹%,.0f ≤ −%s× credit".formatted(pnl, limits.stopLossXCredit())));
    }
    if (dte <= limits.exitDte()) {
      return Optional.of(
          new ExitSignal("time_exit", "%d DTE ≤ exit at %d".formatted(dte, limits.exitDte())));
    }
    return Optional.empty();
  }

  /** Flatten everything when the day's P&L reaches −{@code stopPct}% of capital. */
  public static Optional<ExitSignal> portfolioStop(double dayPnl, double capital, double stopPct) {
    double limit = -capital * stopPct / 100;
    if (dayPnl <= limit) {
      return Optional.of(
          new ExitSignal(
              "portfolio_stop",
              "day P&L ₹%,.0f hit ₹%,.0f; flatten everything".formatted(dayPnl, limit)));
    }
    return Optional.empty();
  }
}
