package com.optionsdesk.trading.kite.adapter;

import com.optionsdesk.shared.domain.OptionContract;
import com.optionsdesk.shared.domain.Quote;
import com.optionsdesk.trading.execution.port.QuoteSource;
import com.optionsdesk.trading.kite.client.KiteClient;
import com.optionsdesk.trading.kite.client.KiteException;
import java.util.List;
import java.util.Optional;

/**
 * Quotes for the paper broker from Kite's REST quote API (exchange-prefixed symbols, e.g. {@code
 * NFO:…}). Kite's own timestamp is kept, so the broker's staleness check sees how old the price
 * really is. A Kite failure is an empty quote: the broker then rejects the order rather than invent
 * a price.
 */
public final class KiteQuoteSource implements QuoteSource {

  private final KiteClient kite;
  private final String exchange;

  public KiteQuoteSource(KiteClient kite, String exchange) {
    this.kite = kite;
    this.exchange = exchange;
  }

  @Override
  public Optional<Quote> latest(OptionContract contract) {
    String id = exchange + ":" + contract.tradingSymbol();
    try {
      return Optional.ofNullable(kite.quotes(List.of(id)).get(id))
          .map(q -> new Quote(contract, q.ltp(), q.bid(), q.ask(), q.at()));
    } catch (KiteException e) {
      return Optional.empty();
    }
  }
}
