package com.optionsdesk.trading.risk.model;

import java.util.List;

/**
 * Portfolio-wide risk limits from the rulebook ({@code risk:}). Percentages are of capital unless
 * stated.
 *
 * @param portfolioDailyStopPct no new trades (and flatten) when the day's P&L falls to −this % of
 *     capital
 * @param perTradeMaxLossPct the most one trade may lose
 * @param maxNakedNotionalX naked (undefined-risk) notional across the book, as a multiple of
 *     capital
 * @param maxStrategyMarginPct margin one strategy may use
 * @param maxUndefinedMarginPct margin all undefined-risk positions may use
 * @param minFreeMarginPct margin that must stay free after an entry
 * @param gapTestPct the overnight gap (± %) every new book is stress-tested against
 * @param maxGapLossPct the most the whole book may lose on that gap
 * @param blackoutDates ISO dates with no new option selling
 */
public record RiskLimits(
    double portfolioDailyStopPct,
    double perTradeMaxLossPct,
    double maxNakedNotionalX,
    double maxStrategyMarginPct,
    double maxUndefinedMarginPct,
    double minFreeMarginPct,
    double gapTestPct,
    double maxGapLossPct,
    List<String> blackoutDates) {

  public RiskLimits {
    blackoutDates = blackoutDates == null ? List.of() : List.copyOf(blackoutDates);
  }
}
