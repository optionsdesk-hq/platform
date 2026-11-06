package com.optionsdesk.trading.risk;

/**
 * One strategy's rulebook limits used by the risk rules and exits.
 *
 * @param enabled false: the strategy may not open anything
 * @param profitTargetPct take profit at this % of the entry credit
 * @param stopLossXCredit stop when the loss reaches this multiple of the entry credit
 * @param exitDte close when this many days (or fewer) remain to expiry
 * @param minCreditToWidth spreads must collect at least this fraction of their width (0 = no
 *     minimum)
 * @param holdsOvernight true for strategies that keep positions overnight (STBT, swing): no naked
 *     legs allowed
 */
public record StrategyLimits(
    boolean enabled,
    double profitTargetPct,
    double stopLossXCredit,
    int exitDte,
    double minCreditToWidth,
    boolean holdsOvernight) {}
