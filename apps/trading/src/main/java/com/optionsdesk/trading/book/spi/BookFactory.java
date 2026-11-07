package com.optionsdesk.trading.book.spi;

import java.time.LocalDate;
import java.util.Optional;

/**
 * Creates a book for a session, or none (e.g. the book is paused, or doesn't trade that day).
 * Strategy plug-ins provide these as beans; the engine asks each one at the start of every session.
 */
@FunctionalInterface
public interface BookFactory {

  Optional<MinuteBook> forDay(LocalDate day, ContractLookup contracts);
}
