package com.optionsdesk.trading.book;

import com.optionsdesk.trading.strategy.MinuteMarket;
import java.time.LocalDate;
import java.time.LocalTime;

/** One session's market, filled a minute at a time; books read it as a {@link MinuteMarket}. */
public interface DayFeed extends MinuteMarket {

  /**
   * Take the snapshot for {@code minute}. Throws if it couldn't (no decision is made on a missing
   * minute).
   */
  void capture(LocalTime minute);

  /** The contracts this feed quotes, for books to trade. */
  ContractLookup contracts();

  /** Opens the day's feed (instruments, the day's option chain). */
  @FunctionalInterface
  interface Factory {
    DayFeed open(LocalDate day);
  }
}
