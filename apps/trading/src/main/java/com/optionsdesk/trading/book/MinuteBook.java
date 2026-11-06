package com.optionsdesk.trading.book;

import com.optionsdesk.trading.strategy.MinuteMarket;
import java.time.LocalTime;

/**
 * A book that acts once a minute during the session. The engine calls one book from one thread, in
 * minute order, so an implementation needs no locking of its own.
 */
public interface MinuteBook {

  /** Unique, e.g. {@code intraday-b}. */
  String id();

  /** Minute {@code minute} has been captured into {@code market}. */
  void onMinute(LocalTime minute, MinuteMarket market);

  /** The session is over: close anything still open. */
  void finish(MinuteMarket market);
}
