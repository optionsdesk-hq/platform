package com.optionsdesk.trading.strategy;

import com.optionsdesk.shared.domain.Right;
import java.time.LocalTime;
import java.util.Collections;
import java.util.List;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * One session's market as minute snapshots, for strategies that act minute by minute. Implemented
 * by the live recorder's feed (live), and by recorded sessions (replay tests and backtests): the
 * same strategy code runs on both.
 */
public interface MinuteMarket {

  /**
   * A contract's last trade, best bid/ask (0 = that side was empty) and cumulative day volume at a
   * minute.
   */
  record Snapshot(double ltp, double bid, double ask, long volume) {

    public Snapshot(double ltp, double bid, double ask) {
      this(ltp, bid, ask, 0);
    }

    /** What a sale fills at: the bid, or the last trade when there is no bid. */
    public double sellPrice() {
      return bid > 0 ? bid : ltp;
    }

    /** What a purchase fills at: the ask, or the last trade when there is no ask. */
    public double buyPrice() {
      return ask > 0 ? ask : ltp;
    }
  }

  /** The near-month index future at a minute: last trade and cumulative day volume. */
  record FutureTick(double ltp, long volume) {}

  /** Every minute of the session that has an index level, in order. */
  List<LocalTime> minutes();

  /** The index level at {@code minute}, if recorded. */
  OptionalDouble spot(LocalTime minute);

  /** The contract's latest snapshot at or before {@code minute}. */
  Optional<Snapshot> quote(double strike, Right right, LocalTime minute);

  /** The near-month future's recorded minutes so far (none by default). */
  default NavigableMap<LocalTime, FutureTick> future() {
    return Collections.emptyNavigableMap();
  }
}
