package com.optionsdesk.trading.kite.adapter;

import com.optionsdesk.shared.domain.OptionContract;
import com.optionsdesk.shared.domain.Quote;
import com.optionsdesk.shared.domain.Right;
import com.optionsdesk.trading.book.spi.ContractLookup;
import com.optionsdesk.trading.book.spi.DayFeed;
import com.optionsdesk.trading.kite.client.KiteClient;
import com.optionsdesk.trading.kite.client.KiteException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;

/**
 * One session of an index and its nearest-expiry option chain from Kite: one quote request a minute
 * for the index and every chain contract (≤ 500). Snapshots accumulate, so books can look back
 * (indicators, VWAPs). Safe for one writer (the engine) and concurrent readers (books).
 */
public final class KiteDayFeed implements DayFeed {

  private final KiteClient kite;
  private final String indexId;
  private final String exchange;
  private final Map<String, OptionContract> chain; // "NFO:SYMBOL" -> contract
  private final Map<String, OptionContract> byStrike = new ConcurrentHashMap<>();
  private final NavigableMap<LocalTime, Double> spot = new ConcurrentSkipListMap<>();
  private final Map<String, NavigableMap<LocalTime, Snapshot>> quotes = new ConcurrentHashMap<>();
  private final Clock clock;
  private volatile LocalTime lastMinute;
  private volatile Instant lastAt;

  KiteDayFeed(
      KiteClient kite, String indexId, String exchange, List<OptionContract> chain, Clock clock) {
    this.clock = clock;
    this.kite = kite;
    this.indexId = indexId;
    this.exchange = exchange;
    this.chain = new ConcurrentHashMap<>();
    for (OptionContract c : chain) {
      this.chain.put(exchange + ":" + c.tradingSymbol(), c);
      byStrike.put(key(c.strike(), c.right()), c);
    }
  }

  /**
   * The day's feed: the index level now, the nearest expiry on or after {@code day}, and its
   * strikes within {@code width} points of the index.
   */
  public static KiteDayFeed open(
      KiteClient kite,
      LocalDate day,
      String indexId,
      String exchange,
      String underlying,
      double width,
      Clock clock) {
    var index = kite.quotes(List.of(indexId)).get(indexId);
    if (index == null || index.ltp() <= 0) {
      throw new KiteException(KiteException.Kind.API, "no level for " + indexId);
    }
    var options = kite.options(exchange, underlying);
    LocalDate expiry =
        options.stream()
            .map(i -> i.contract().expiry())
            .filter(e -> !e.isBefore(day))
            .min(Comparator.naturalOrder())
            .orElseThrow(
                () ->
                    new KiteException(
                        KiteException.Kind.API, "no " + underlying + " expiry on or after " + day));
    List<OptionContract> chain = new ArrayList<>();
    for (var i : options) {
      var c = i.contract();
      if (c.expiry().equals(expiry) && Math.abs(c.strike() - index.ltp()) <= width) {
        chain.add(c);
      }
    }
    return new KiteDayFeed(kite, indexId, exchange, chain, clock);
  }

  @Override
  public void capture(LocalTime minute) {
    List<String> ids = new ArrayList<>(chain.size() + 1);
    ids.add(indexId);
    ids.addAll(chain.keySet());
    var got = kite.quotes(ids);
    var index = got.get(indexId);
    if (index == null || index.ltp() <= 0) {
      throw new KiteException(KiteException.Kind.API, "no index level at " + minute);
    }
    for (var e : chain.entrySet()) {
      var q = got.get(e.getKey());
      if (q != null && q.ltp() > 0) {
        quotes
            .computeIfAbsent(
                key(e.getValue().strike(), e.getValue().right()),
                k -> new ConcurrentSkipListMap<>())
            .put(minute, new Snapshot(q.ltp(), q.bid(), q.ask(), q.volume()));
      }
    }
    spot.put(minute, index.ltp()); // last: a minute appears only once its quotes are in
    lastAt = clock.instant();
    lastMinute = minute;
  }

  @Override
  public Optional<Quote> latest(OptionContract contract) {
    LocalTime m = lastMinute;
    OptionContract ours = byStrike.get(key(contract.strike(), contract.right()));
    if (m == null || !contract.equals(ours)) {
      return Optional.empty();
    }
    return quote(contract.strike(), contract.right(), m)
        .map(s -> new Quote(contract, s.ltp(), s.bid(), s.ask(), lastAt));
  }

  @Override
  public ContractLookup contracts() {
    return (strike, right) -> Optional.ofNullable(byStrike.get(key(strike, right)));
  }

  @Override
  public List<LocalTime> minutes() {
    return new ArrayList<>(spot.keySet());
  }

  @Override
  public OptionalDouble spot(LocalTime minute) {
    Double s = spot.get(minute);
    return s == null ? OptionalDouble.empty() : OptionalDouble.of(s);
  }

  @Override
  public Optional<Snapshot> quote(double strike, Right right, LocalTime minute) {
    var s = quotes.get(key(strike, right));
    return s == null
        ? Optional.empty()
        : Optional.ofNullable(s.floorEntry(minute)).map(Map.Entry::getValue);
  }

  /** The exchange prefix used for quotes, e.g. NFO. */
  String exchange() {
    return exchange;
  }

  /** Contracts quoted every minute. */
  int chainSize() {
    return chain.size();
  }

  private static String key(double strike, Right right) {
    return Math.round(strike * 100) + right.name();
  }
}
