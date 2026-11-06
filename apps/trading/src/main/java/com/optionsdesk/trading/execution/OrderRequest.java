package com.optionsdesk.trading.execution;

import com.optionsdesk.shared.domain.OptionContract;
import java.util.Objects;

/**
 * One order to send: buy or sell {@code quantity} units of a contract.
 *
 * @param tag idempotency key, unique per order (the broker refuses a repeated tag)
 */
public record OrderRequest(OptionContract contract, Side side, int quantity, String tag) {

  /** Buy or sell. */
  public enum Side {
    BUY,
    SELL
  }

  public OrderRequest {
    Objects.requireNonNull(contract, "contract");
    Objects.requireNonNull(side, "side");
    Objects.requireNonNull(tag, "tag");
    if (quantity <= 0 || quantity % contract.lotSize() != 0) {
      throw new IllegalArgumentException(
          "quantity must be a positive multiple of the lot size "
              + contract.lotSize()
              + ", got "
              + quantity);
    }
  }

  public boolean isSell() {
    return side == Side.SELL;
  }
}
