package com.optionsdesk.trading.risk;

import com.optionsdesk.trading.risk.RiskRules.Check;
import java.util.List;
import java.util.Optional;

/**
 * Runs every rule on a proposal. A proposal is allowed only if every applicable rule passes; the
 * full list of checks is kept so the journal and the UI can show exactly why something was refused.
 * Fail-closed: an exception in a rule propagates (nothing opens) rather than being treated as a
 * pass.
 */
public final class PreTradeRiskEngine {

  /** The outcome: allowed or not, and every check that applied. */
  public record Verdict(boolean allowed, List<Check> checks) {
    public Verdict {
      checks = List.copyOf(checks);
    }

    public List<Check> failures() {
      return checks.stream().filter(c -> !c.ok()).toList();
    }
  }

  private final List<RiskRules.Rule> rules;

  public PreTradeRiskEngine() {
    this(RiskRules.ALL);
  }

  PreTradeRiskEngine(List<RiskRules.Rule> rules) {
    this.rules = List.copyOf(rules);
  }

  public Verdict evaluate(RuleContext context) {
    List<Check> checks =
        rules.stream().map(r -> r.apply(context)).flatMap(Optional::stream).toList();
    return new Verdict(checks.stream().allMatch(Check::ok), checks);
  }
}
