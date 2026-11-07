package com.optionsdesk.trading.risk.model;

import com.optionsdesk.shared.domain.PayoffStats;
import com.optionsdesk.shared.domain.Structure;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Everything a pre-trade rule may look at, computed once per proposal. Rules are pure functions of
 * this record: no I/O, no clock, so each one is trivially testable.
 *
 * @param capital trading capital in ₹
 * @param limits portfolio risk limits
 * @param strategy the proposing strategy's limits
 * @param proposal the structure to open
 * @param openPositions structures already open
 * @param stats the proposal's payoff statistics
 * @param spot index level now
 * @param dayPnl today's P&L so far (₹)
 * @param today the trading day
 * @param newMargin margin the proposal needs (₹)
 * @param usedMargin margin already used by open positions (₹)
 * @param sameStrategyMargin margin used by open positions of the same strategy (₹)
 * @param undefinedMargin margin used by open undefined-risk positions (₹)
 * @param nakedNotionalTotal naked notional of the book including the proposal (₹)
 * @param eventToday a scheduled event that blocks selling today, when events are configured to
 *     block
 */
public record RuleContext(
    double capital,
    RiskLimits limits,
    StrategyLimits strategy,
    Structure proposal,
    List<Structure> openPositions,
    PayoffStats stats,
    double spot,
    double dayPnl,
    LocalDate today,
    double newMargin,
    double usedMargin,
    double sameStrategyMargin,
    double undefinedMargin,
    double nakedNotionalTotal,
    Optional<String> eventToday) {

  public RuleContext {
    openPositions = List.copyOf(openPositions);
  }

  /** ₹ amount for a percentage of capital. */
  public double pct(double percent) {
    return capital * percent / 100;
  }
}
