package com.optionsdesk.trading.book.service;

import com.optionsdesk.shared.calendar.MarketCalendar;
import com.optionsdesk.trading.journal.model.Book;
import com.optionsdesk.trading.journal.model.Position;
import com.optionsdesk.trading.journal.repository.BookRepository;
import com.optionsdesk.trading.journal.repository.PositionJournal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/**
 * The use cases behind the books API: what each book is and does today, and the owner's controls.
 * Pausing and resuming take effect from the next session (the engine asks each book's factory at
 * the open, and a factory respects the state). Exiting acts now: the book closes what it holds and
 * stops for the day, and it is paused so it doesn't start again tomorrow. Works with the engine off
 * (then only the journal side applies).
 */
@Service
public class BookService {

  /** A book with today's picture. */
  public record BookStatus(Book book, String today, long openPositions, double realisedToday) {}

  /** What an owner action did. */
  public record ActionOutcome(boolean done, String message) {}

  private final BookRepository books;
  private final PositionJournal positions;
  private final ObjectProvider<MinuteEngine> engine;
  private final Clock clock;

  public BookService(
      BookRepository books,
      PositionJournal positions,
      ObjectProvider<MinuteEngine> engine,
      Clock clock) {
    this.books = books;
    this.positions = positions;
    this.engine = engine;
    this.clock = clock;
  }

  public List<BookStatus> books() {
    Map<String, String> running = engineBooks();
    Instant since = startOfToday();
    return books.findAll().stream()
        .map(
            b ->
                new BookStatus(
                    b,
                    running.getOrDefault(b.id(), "not running"),
                    positions.countOpen(b.id()),
                    positions.realisedSince(b.id(), since)))
        .toList();
  }

  public Optional<List<Position>> positions(String bookId, Optional<String> status, int limit) {
    if (books.find(bookId).isEmpty()) {
      return Optional.empty();
    }
    int n = Math.max(1, Math.min(limit, 500));
    return Optional.of(
        status
            .map(s -> positions.find(bookId, s.toUpperCase(java.util.Locale.ROOT)))
            .orElseGet(() -> positions.recent(bookId, n)));
  }

  /** The engine's picture: today's session and each book's state, or empty when it's off. */
  public Optional<EngineStatus> engine() {
    MinuteEngine e = engine.getIfAvailable();
    if (e == null) {
      return Optional.empty();
    }
    return Optional.of(new EngineStatus(e.day(), e.finished(), e.books(), e.lastProblem()));
  }

  /** The engine's state. */
  public record EngineStatus(
      Optional<LocalDate> day,
      boolean finished,
      Map<String, String> books,
      Optional<String> lastProblem) {}

  public ActionOutcome pause(String bookId) {
    return setState(bookId, "PAUSED", "paused: no new session from tomorrow");
  }

  public ActionOutcome resume(String bookId) {
    return setState(bookId, "ACTIVE", "active: trades from the next session");
  }

  /** Exit the whole strategy: close its open legs now, stop it today, pause it. */
  public ActionOutcome exit(String bookId, String reason) {
    if (books.find(bookId).isEmpty()) {
      return new ActionOutcome(false, "no book " + bookId);
    }
    books.setState(bookId, "PAUSED");
    MinuteEngine e = engine.getIfAvailable();
    boolean asked = e != null && e.exit(bookId, reason);
    return new ActionOutcome(
        true,
        asked
            ? "exiting now (closing open legs) and paused"
            : "paused; it isn't running today, so nothing to close");
  }

  /** The kill switch: every book exits now and is paused. */
  public ActionOutcome killAll(String reason) {
    books.findAll().forEach(b -> books.setState(b.id(), "PAUSED"));
    MinuteEngine e = engine.getIfAvailable();
    List<String> asked = e == null ? List.of() : e.exitAll(reason);
    return new ActionOutcome(
        true, "all books paused; exiting now: " + (asked.isEmpty() ? "none running" : asked));
  }

  private ActionOutcome setState(String bookId, String state, String message) {
    return books.setState(bookId, state)
        ? new ActionOutcome(true, message)
        : new ActionOutcome(false, "no book " + bookId);
  }

  private Map<String, String> engineBooks() {
    MinuteEngine e = engine.getIfAvailable();
    return e == null ? Map.of() : e.books();
  }

  private Instant startOfToday() {
    return LocalDate.now(clock.withZone(MarketCalendar.IST))
        .atStartOfDay(MarketCalendar.IST)
        .toInstant();
  }
}
