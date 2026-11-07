package com.optionsdesk.trading.book.spi;

import com.optionsdesk.trading.strategy.model.MinuteMarket;
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

  /**
   * Exit now (owner or kill switch): close everything this book holds and act no more today. The
   * engine calls it on the book's own thread. Books that can't do it say so.
   */
  default void exitNow(MinuteMarket market, String reason) {
    throw new UnsupportedOperationException(id() + " can't exit on demand");
  }

  /** One line for the end-of-day alert (empty = nothing to report). */
  default String summary() {
    return "";
  }
}
