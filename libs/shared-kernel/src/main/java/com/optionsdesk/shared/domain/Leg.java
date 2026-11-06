package com.optionsdesk.shared.domain;

import java.util.Objects;

/**
 * One leg of a position: a number of lots of one contract, bought (positive lots) or sold (negative
 * lots), at an entry price per unit.
 *
 * @param contract the contract
 * @param lots signed lots: positive = long, negative = short; never zero
 * @param entryPrice price per unit paid (long) or received (short)
 */
public record Leg(OptionContract contract, int lots, double entryPrice) {

  public Leg {
    Objects.requireNonNull(contract, "contract");
    if (lots == 0) {
      throw new IllegalArgumentException("a leg needs at least one lot");
    }
    if (entryPrice < 0) {
      throw new IllegalArgumentException("entryPrice can't be negative");
    }
  }

  /** Signed quantity in units (lots × lot size). */
  public int quantity() {
    return lots * contract.lotSize();
  }

  public boolean isShort() {
    return lots < 0;
  }

  /** Profit or loss in ₹ if the contract expires with the index at {@code spot}. */
  public double payoffAtExpiry(double spot) {
    return (contract.right().intrinsic(spot, contract.strike()) - entryPrice) * quantity();
  }

  /** Profit or loss in ₹ if the leg were closed now at {@code price} per unit. */
  public double markToMarket(double price) {
    return (price - entryPrice) * quantity();
  }
}
