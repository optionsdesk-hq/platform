package com.optionsdesk.trading.execution;

import java.time.Instant;

/**
 * A filled order: price per unit, units, all charges in ₹, and when.
 *
 * @param source what the price came from, e.g. "bid", "ask", "mid-slipped"
 */
public record Execution(
    OrderRequest request, double price, int quantity, double charges, Instant at, String source) {

  /** ₹ received (positive, a sale) or paid (negative, a purchase), before charges. */
  public double cashFlow() {
    return request.isSell() ? price * quantity : -price * quantity;
  }
}
