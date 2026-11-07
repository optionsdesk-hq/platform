package com.optionsdesk.trading.book.spi;

import com.optionsdesk.shared.domain.OptionContract;
import com.optionsdesk.shared.domain.Quote;
import com.optionsdesk.trading.strategy.model.MinuteMarket;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;

/** One session's market, filled a minute at a time; books read it as a {@link MinuteMarket}. */
public interface DayFeed extends MinuteMarket {

  /**
   * Take the snapshot for {@code minute}. Throws if it couldn't (no decision is made on a missing
   * minute).
   */
  void capture(LocalTime minute);

  /**
   * The contract's quote from the latest capture, stamped with when it was captured (empty when not
   * quoted). Lets paper books fill from the snapshot they decided on, with no extra request.
   */
  default Optional<Quote> latest(OptionContract contract) {
    return Optional.empty();
  }

  /** The contracts this feed quotes, for books to trade. */
  ContractLookup contracts();

  /** Opens the day's feed (instruments, the day's option chain). */
  @FunctionalInterface
  interface Factory {
    DayFeed open(LocalDate day);
  }
}
