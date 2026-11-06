package com.optionsdesk.trading.risk;

import static org.assertj.core.api.Assertions.assertThat;

import com.optionsdesk.shared.domain.Leg;
import com.optionsdesk.shared.domain.OptionContract;
import com.optionsdesk.shared.domain.PayoffStats;
import com.optionsdesk.shared.domain.Right;
import com.optionsdesk.shared.domain.Structure;
import com.optionsdesk.trading.risk.RiskRules.Check;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Readable examples of each rule; the golden test proves them against the legacy system at scale.
 */
class RiskRulesTest {

  static final LocalDate EXPIRY = LocalDate.of(2026, 11, 17);
  static final RiskLimits LIMITS =
      new RiskLimits(2, 1, 2.5, 25, 10, 40, 10, 5, List.of("2026-11-12"));

  /** Example limits for an overnight credit spread (sample values, not the rulebook). */
  static final StrategyLimits SPREAD = new StrategyLimits(true, 50, 1, 0, 0.2, true);

  static Leg leg(double strike, Right right, int lots, double entry) {
    return new Leg(
        new OptionContract("S" + (int) strike + right, "NIFTY", EXPIRY, strike, right, 65),
        lots,
        entry);
  }

  /** A bull put spread, 2 lots: short 24500 PE at 80, wing 24200 PE at 20. */
  static final Structure BULL_PUT =
      new Structure(List.of(leg(24_500, Right.PE, -2, 80), leg(24_200, Right.PE, 2, 20)), true);

  static RuleContext context(
      Structure proposal, StrategyLimits strategy, double dayPnl, Optional<String> event) {
    return new RuleContext(
        2_000_000, // sample capital, not the rulebook's
        LIMITS,
        strategy,
        proposal,
        List.of(),
        PayoffStats.of(proposal, 24_600),
        24_600,
        dayPnl,
        LocalDate.of(2026, 11, 11),
        60_000,
        200_000,
        0,
        0,
        0,
        event);
  }

  static Optional<Check> failed(PreTradeRiskEngine.Verdict v, String rule) {
    return v.failures().stream().filter(c -> c.rule().equals(rule)).findFirst();
  }

  /**
   * One lot of a 300-wide bull put passes every rule. Two lots risk ₹31,200, above a 1% per-trade
   * limit on ₹20L (₹20,000), so the engine refuses them.
   */
  @Test
  void oneLotPassesTwoLotsExceedThePerTradeLimit() {
    var oneLot =
        new Structure(List.of(leg(24_500, Right.PE, -1, 80), leg(24_200, Right.PE, 1, 20)), true);
    var verdict = new PreTradeRiskEngine().evaluate(context(oneLot, SPREAD, 0, Optional.empty()));
    assertThat(verdict.failures()).isEmpty();
    assertThat(verdict.checks())
        .extracting(Check::rule)
        .contains("no_naked_overnight", "credit_to_width");

    var twoLots = new PreTradeRiskEngine().evaluate(context(BULL_PUT, SPREAD, 0, Optional.empty()));
    assertThat(twoLots.allowed()).isFalse();
    assertThat(failed(twoLots, "per_trade_loss"))
        .hasValueSatisfying(c -> assertThat(c.detail()).contains("31,200").contains("20,000"));
  }

  /** Owner's rule: no naked legs overnight, whatever any other number says. */
  @Test
  void nakedOvernightIsRefused() {
    var naked = new Structure(List.of(leg(24_500, Right.PE, -2, 80)), false);
    var verdict = new PreTradeRiskEngine().evaluate(context(naked, SPREAD, 0, Optional.empty()));
    assertThat(verdict.allowed()).isFalse();
    assertThat(failed(verdict, "no_naked_overnight")).isPresent();
    var intraday = new StrategyLimits(true, 50, 1, 0, 0, false);
    assertThat(
            new PreTradeRiskEngine()
                .evaluate(context(naked, intraday, 0, Optional.empty()))
                .checks())
        .extracting(Check::rule)
        .doesNotContain("no_naked_overnight"); // intraday: naked allowed (with hard stops)
  }

  @Test
  void dailyStopAndEventsBlockNewTrades() {
    var engine = new PreTradeRiskEngine();
    assertThat(
            failed(
                engine.evaluate(context(BULL_PUT, SPREAD, -40_000, Optional.empty())),
                "daily_stop"))
        .isPresent(); // exactly −2% of ₹20L
    assertThat(
            failed(
                engine.evaluate(context(BULL_PUT, SPREAD, 0, Optional.of("RBI policy"))),
                "blackout"))
        .hasValueSatisfying(c -> assertThat(c.detail()).contains("RBI policy"));
  }

  @Test
  void aDisabledStrategyCannotOpen() {
    var off = new StrategyLimits(false, 50, 1, 0, 0, true);
    assertThat(
            failed(
                new PreTradeRiskEngine().evaluate(context(BULL_PUT, off, 0, Optional.empty())),
                "strategy_enabled"))
        .isPresent();
  }

  @Test
  void exitsInOrderOfPrecedence() {
    var limits = new StrategyLimits(true, 50, 1, 7, 0, false);
    assertThat(Exits.forPosition(1000, 500, 20, limits))
        .hasValueSatisfying(e -> assertThat(e.reason()).isEqualTo("profit_target"));
    assertThat(Exits.forPosition(1000, -1000, 20, limits))
        .hasValueSatisfying(e -> assertThat(e.reason()).isEqualTo("stop_loss"));
    assertThat(Exits.forPosition(1000, 100, 7, limits))
        .hasValueSatisfying(e -> assertThat(e.reason()).isEqualTo("time_exit"));
    assertThat(Exits.forPosition(1000, 100, 8, limits)).isEmpty();
    assertThat(Exits.portfolioStop(-40_000, 2_000_000, 2)).isPresent();
    assertThat(Exits.portfolioStop(-39_999, 2_000_000, 2)).isEmpty();
  }
}
