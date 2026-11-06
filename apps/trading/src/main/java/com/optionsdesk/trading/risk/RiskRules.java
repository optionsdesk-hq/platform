package com.optionsdesk.trading.risk;

import com.optionsdesk.shared.domain.Structure;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * The rulebook's pre-trade rules. Each rule is a pure function of a {@link RuleContext} that
 * returns a {@link Check}, or empty when it doesn't apply to the proposal. Ported from the legacy
 * Python {@code risk/rules.py} (same names, same order, golden-tested) plus the owner's
 * no-naked-overnight rule.
 *
 * <p>Adding a rule = one method here, added to {@link #ALL}, plus tests.
 */
public final class RiskRules {

  /** One rule's verdict: its name, pass/fail, and the numbers behind it. */
  public record Check(String rule, boolean ok, String detail) {}

  /** A rule: applies to a proposal (a check) or not (empty). */
  public interface Rule extends Function<RuleContext, Optional<Check>> {}

  private RiskRules() {}

  static Optional<Check> strategyEnabled(RuleContext c) {
    boolean on = c.strategy().enabled();
    return check("strategy_enabled", on, on ? "enabled" : "disabled in the rulebook");
  }

  static Optional<Check> dailyStop(RuleContext c) {
    double limit = c.pct(c.limits().portfolioDailyStopPct());
    return check(
        "daily_stop",
        c.dayPnl() > -limit,
        "day P&L ₹%,.0f vs stop −₹%,.0f".formatted(c.dayPnl(), limit));
  }

  static Optional<Check> blackout(RuleContext c) {
    if (c.eventToday().isPresent()) {
      return check(
          "blackout", false, "%s is an event day: %s".formatted(c.today(), c.eventToday().get()));
    }
    boolean blocked = c.limits().blackoutDates().contains(c.today().toString());
    return check(
        "blackout",
        !blocked,
        "%s %s an event blackout".formatted(c.today(), blocked ? "is" : "not"));
  }

  static Optional<Check> perTradeLoss(RuleContext c) {
    double limit = c.pct(c.limits().perTradeMaxLossPct());
    if (c.proposal().definedRisk()) {
      double worst = Math.abs(c.stats().maxLoss());
      return check(
          "per_trade_loss",
          worst <= limit,
          "max loss ₹%,.0f vs limit ₹%,.0f".formatted(worst, limit));
    }
    double planned = c.proposal().credit() * c.strategy().stopLossXCredit();
    return check(
        "per_trade_loss",
        planned <= limit,
        "planned stop ₹%,.0f vs limit ₹%,.0f".formatted(planned, limit));
  }

  static Optional<Check> nakedNotional(RuleContext c) {
    double cap = c.capital() * c.limits().maxNakedNotionalX();
    return check(
        "naked_notional",
        c.nakedNotionalTotal() <= cap,
        "naked notional ₹%,.0f vs cap ₹%,.0f".formatted(c.nakedNotionalTotal(), cap));
  }

  static Optional<Check> strategyConcentration(RuleContext c) {
    double total = c.sameStrategyMargin() + c.newMargin();
    double cap = c.pct(c.limits().maxStrategyMarginPct());
    return check(
        "strategy_concentration",
        total <= cap,
        "strategy margin ₹%,.0f vs ₹%,.0f".formatted(total, cap));
  }

  static Optional<Check> undefinedCap(RuleContext c) {
    if (c.proposal().definedRisk()) {
      return Optional.empty();
    }
    double total = c.undefinedMargin() + c.newMargin();
    double cap = c.pct(c.limits().maxUndefinedMarginPct());
    return check(
        "undefined_cap",
        total <= cap,
        "undefined-risk margin ₹%,.0f vs ₹%,.0f".formatted(total, cap));
  }

  static Optional<Check> freeMargin(RuleContext c) {
    double free = c.capital() - c.usedMargin() - c.newMargin();
    double floor = c.pct(c.limits().minFreeMarginPct());
    return check(
        "free_margin",
        free >= floor,
        "free margin after entry ₹%,.0f vs floor ₹%,.0f".formatted(free, floor));
  }

  static Optional<Check> positiveCredit(RuleContext c) {
    double credit = c.proposal().credit();
    return check("positive_credit", credit > 0, "net credit ₹%,.0f".formatted(credit));
  }

  /** The whole book (open positions + proposal) must survive a ±gap move at expiry. */
  static Optional<Check> gapLoss(RuleContext c) {
    double move = c.limits().gapTestPct() / 100;
    List<Structure> book = new java.util.ArrayList<>(c.openPositions());
    book.add(c.proposal());
    double worst = Double.POSITIVE_INFINITY;
    for (double d : new double[] {-move, move}) {
      double level = c.spot() * (1 + d);
      worst = Math.min(worst, book.stream().mapToDouble(s -> s.payoffAt(level)).sum());
    }
    double cap = c.pct(c.limits().maxGapLossPct());
    return check(
        "gap_loss",
        -worst <= cap,
        "book loss on ±%s%% gap ₹%,.0f vs ₹%,.0f".formatted(c.limits().gapTestPct(), -worst, cap));
  }

  static Optional<Check> creditToWidth(RuleContext c) {
    if (c.stats().creditToWidth().isEmpty() || c.strategy().minCreditToWidth() == 0) {
      return Optional.empty();
    }
    double ctw = c.stats().creditToWidth().getAsDouble();
    return check(
        "credit_to_width",
        ctw >= c.strategy().minCreditToWidth(),
        "credit/width %.2f vs min %.2f".formatted(ctw, c.strategy().minCreditToWidth()));
  }

  /**
   * Owner's rule (5 Nov 2026): a strategy that holds overnight may never open a naked
   * (undefined-risk) structure.
   */
  static Optional<Check> noNakedOvernight(RuleContext c) {
    if (!c.strategy().holdsOvernight()) {
      return Optional.empty();
    }
    boolean ok = c.proposal().definedRisk();
    return check(
        "no_naked_overnight",
        ok,
        ok ? "every short leg has its wing" : "naked legs held overnight");
  }

  /** Every rule, in evaluation order (the legacy order, then the owner's additions). */
  public static final List<Rule> ALL =
      List.of(
          RiskRules::strategyEnabled,
          RiskRules::dailyStop,
          RiskRules::blackout,
          RiskRules::perTradeLoss,
          RiskRules::nakedNotional,
          RiskRules::strategyConcentration,
          RiskRules::undefinedCap,
          RiskRules::freeMargin,
          RiskRules::positiveCredit,
          RiskRules::gapLoss,
          RiskRules::creditToWidth,
          RiskRules::noNakedOvernight);

  private static Optional<Check> check(String rule, boolean ok, String detail) {
    return Optional.of(new Check(rule, ok, detail));
  }
}
