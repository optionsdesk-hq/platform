package com.optionsdesk.trading.execution;

import com.optionsdesk.shared.domain.OptionContract;
import com.optionsdesk.shared.domain.Quote;
import java.util.Optional;

/**
 * The latest quote for a contract (the live tick feed, or a recorded session in tests and replays).
 */
@FunctionalInterface
public interface QuoteSource {

  Optional<Quote> latest(OptionContract contract);
}
