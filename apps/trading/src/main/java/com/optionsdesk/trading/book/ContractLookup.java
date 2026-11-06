package com.optionsdesk.trading.book;

import com.optionsdesk.shared.domain.OptionContract;
import com.optionsdesk.shared.domain.Right;
import java.util.Optional;

/** The listed contract for a strike and right in the book's expiry (from the day's instruments). */
@FunctionalInterface
public interface ContractLookup {

  Optional<OptionContract> find(double strike, Right right);
}
