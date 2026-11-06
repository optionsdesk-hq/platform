/**
 * Execution: how orders fill. {@link com.optionsdesk.trading.execution.PaperBroker} fills paper
 * orders at the real bid/ask with real charges; {@link
 * com.optionsdesk.trading.execution.ExecutionService} orders the legs so a position is never naked
 * mid-trade, and unwinds what filled when a leg is rejected. A live broker (Kite, gated by
 * mode=LIVE) implements the same {@link com.optionsdesk.trading.execution.Broker} port later.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Execution")
package com.optionsdesk.trading.execution;
