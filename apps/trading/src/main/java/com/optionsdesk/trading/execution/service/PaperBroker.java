package com.optionsdesk.trading.execution.service;

import com.optionsdesk.shared.money.Charges;
import com.optionsdesk.trading.execution.model.Execution;
import com.optionsdesk.trading.execution.model.OrderRequest;
import com.optionsdesk.trading.execution.port.Broker;
import com.optionsdesk.trading.execution.port.QuoteSource;
import java.time.Clock;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;

/**
 * Paper fills at real prices: a sale fills at the bid, a purchase at the ask. When that side of the
 * book is empty, the mid moved against us by the configured slippage ({@link
 * Charges#slippedPrice}). No price, or a stale quote, rejects the order: paper trading must not
 * invent fills. Charges are the real ones per order.
 */
public final class PaperBroker implements Broker {

  private final QuoteSource quotes;
  private final Charges charges;
  private final Clock clock;
  private final Duration maxQuoteAge;
  private final Set<String> tags = new HashSet<>();

  public PaperBroker(QuoteSource quotes, Charges charges, Clock clock, Duration maxQuoteAge) {
    this.quotes = quotes;
    this.charges = charges;
    this.clock = clock;
    this.maxQuoteAge = maxQuoteAge;
  }

  @Override
  public synchronized Execution execute(OrderRequest r) throws OrderRejected {
    if (tags.contains(r.tag())) {
      throw new OrderRejected(r, "duplicate tag " + r.tag());
    }
    var now = clock.instant();
    var quote = quotes.latest(r.contract()).orElseThrow(() -> new OrderRejected(r, "no quote"));
    if (quote.isStale(now, maxQuoteAge)) {
      throw new OrderRejected(r, "quote is older than " + maxQuoteAge.toSeconds() + " s");
    }
    double side = r.isSell() ? quote.bid() : quote.ask();
    double price;
    String source;
    if (side > 0) {
      price = side;
      source = r.isSell() ? "bid" : "ask";
    } else {
      var mid = quote.mid();
      if (mid.isEmpty()) {
        throw new OrderRejected(r, "no bid, ask or last trade");
      }
      price = charges.slippedPrice(mid.getAsDouble(), !r.isSell());
      source = "mid-slipped";
    }
    tags.add(r.tag());
    double cost = charges.order(price * r.quantity(), r.isSell());
    return new Execution(r, price, r.quantity(), cost, now, source);
  }
}
