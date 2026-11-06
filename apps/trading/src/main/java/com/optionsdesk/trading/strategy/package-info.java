/**
 * The strategy interface: what a strategy reads ({@link
 * com.optionsdesk.trading.strategy.MinuteMarket}, {@link
 * com.optionsdesk.trading.strategy.OptionChain}). Strategies are pure: market data in, decisions
 * out; they hold no positions (books do) and never place orders (risk and execution do). The
 * strategy implementations themselves are private and live outside this repository.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Strategy")
package com.optionsdesk.trading.strategy;
