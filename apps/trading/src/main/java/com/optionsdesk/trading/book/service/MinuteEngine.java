package com.optionsdesk.trading.book.service;

import com.optionsdesk.shared.calendar.MarketCalendar;
import com.optionsdesk.trading.alerts.port.Alerts;
import com.optionsdesk.trading.book.spi.BookFactory;
import com.optionsdesk.trading.book.spi.DayFeed;
import com.optionsdesk.trading.book.spi.MinuteBook;
import com.optionsdesk.trading.execution.port.QuoteSource;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Drives minute books through a session. Called once a minute ({@link #tick}); on a trading day
 * from the open:
 *
 * <ol>
 *   <li>at the first tick of the day, opens the day's feed and asks every {@link BookFactory} for
 *       its book;
 *   <li>each minute, captures the market once, then hands that minute to every book on the book's
 *       own executor;
 *   <li>after the close, tells every book to finish, once.
 * </ol>
 *
 * A failed capture skips the minute for every book (no decision on missing prices). A book that
 * throws is stopped for the rest of the day; the others carry on. A feed that can't open (e.g. no
 * broker session yet) is retried the next minute.
 */
public final class MinuteEngine {

  private static final Logger log = LoggerFactory.getLogger(MinuteEngine.class);

  private final MarketCalendar calendar;
  private final DayFeed.Factory feeds;
  private final List<BookFactory> factories;
  private final Function<String, Executor> executors;
  private final Alerts alerts;
  private final Set<String> alerted = new HashSet<>();

  private LocalDate day;
  private volatile DayFeed feed;
  private final Map<String, Running> books = new LinkedHashMap<>();
  private boolean finished;
  private Optional<String> lastProblem = Optional.empty();

  private record Running(MinuteBook book, Executor executor, AtomicReference<String> failure) {}

  public MinuteEngine(
      MarketCalendar calendar,
      DayFeed.Factory feeds,
      List<BookFactory> factories,
      Function<String, Executor> executors,
      Alerts alerts) {
    this.calendar = calendar;
    this.feeds = feeds;
    this.factories = List.copyOf(factories);
    this.executors = executors;
    this.alerts = alerts;
  }

  /** One minute of the engine at {@code now} (any zone; IST is used). */
  public synchronized void tick(ZonedDateTime now) {
    var t = now.withZoneSameInstant(MarketCalendar.IST);
    LocalDate d = t.toLocalDate();
    LocalTime m = t.toLocalTime().truncatedTo(ChronoUnit.MINUTES);
    if (!calendar.isTradingDay(d) || m.isBefore(MarketCalendar.OPEN)) {
      return;
    }
    if (!d.equals(day)) {
      if (m.isAfter(MarketCalendar.CLOSE)) {
        return; // never open a session after its close
      }
      if (!openDay(d)) {
        return;
      }
    }
    if (finished) {
      return;
    }
    if (m.isAfter(MarketCalendar.CLOSE)) {
      for (Running r : books.values()) {
        dispatch(r, () -> r.book().finish(feed));
        r.executor().execute(() -> summarise(r));
      }
      finished = true;
      return;
    }
    try {
      feed.capture(m);
    } catch (RuntimeException e) {
      problem("capture", "%s capture failed, minute skipped: %s".formatted(m, e.getMessage()));
      return;
    }
    for (Running r : books.values()) {
      dispatch(r, () -> r.book().onMinute(m, feed));
    }
  }

  /**
   * Exit one of today's books now: it closes what it holds (on its own thread) and is stopped for
   * the rest of the day. False when the book isn't running today.
   */
  public synchronized boolean exit(String bookId, String reason) {
    Running r = books.get(bookId);
    if (r == null || r.failure().get() != null || feed == null || finished) {
      return false;
    }
    DayFeed f = feed;
    r.executor()
        .execute(
            () -> {
              try {
                r.book().exitNow(f, reason);
              } catch (RuntimeException e) {
                problem("exit:" + bookId, "book %s exit failed: %s".formatted(bookId, e));
              }
              r.failure().compareAndSet(null, "exited: " + reason);
            });
    log.warn("book {} exiting: {}", bookId, reason);
    return true;
  }

  /** Exit every book running today; returns the ones asked to exit. */
  public synchronized List<String> exitAll(String reason) {
    List<String> asked = new ArrayList<>();
    for (String id : books.keySet()) {
      if (exit(id, reason)) {
        asked.add(id);
      }
    }
    return asked;
  }

  private boolean openDay(LocalDate d) {
    DayFeed f;
    try {
      f = feeds.open(d);
    } catch (RuntimeException e) {
      problem("feed", "%s feed not open yet: %s".formatted(d, e.getMessage()));
      return false;
    }
    day = d;
    feed = f;
    finished = false;
    books.clear();
    for (BookFactory factory : factories) {
      factory
          .forDay(d, f.contracts())
          .ifPresent(
              b ->
                  books.put(
                      b.id(), new Running(b, executors.apply(b.id()), new AtomicReference<>())));
    }
    log.info("session {} opened with books {}", d, books.keySet());
    alerts.info("session %s opened: books %s".formatted(d, books.keySet()));
    return true;
  }

  private void dispatch(Running r, Runnable work) {
    if (r.failure().get() != null) {
      return;
    }
    r.executor()
        .execute(
            () -> {
              if (r.failure().get() != null) {
                return;
              }
              try {
                work.run();
              } catch (RuntimeException e) {
                r.failure().set(e.toString());
                problem(
                    "book:" + r.book().id(),
                    "book %s stopped for the day: %s".formatted(r.book().id(), e));
              }
            });
  }

  /** Logged every time; alerted (urgent) once per kind per day, so an outage isn't 375 messages. */
  private void problem(String kind, String what) {
    lastProblem = Optional.of(what);
    log.warn(what);
    if (alerted.add(kind + "@" + (day == null ? "-" : day))) {
      alerts.urgent(what);
    }
  }

  private void summarise(Running r) {
    try {
      String s = r.book().summary();
      if (!s.isBlank()) {
        alerts.info(r.book().id() + ": " + s);
      }
    } catch (RuntimeException e) {
      log.warn("summary of {} failed: {}", r.book().id(), e.toString());
    }
  }

  /**
   * Quotes from today's latest capture (stamped with the capture time): paper books fill from the
   * snapshot they decided on, with no extra broker request. Empty before the first capture.
   */
  public QuoteSource quotes() {
    return contract -> {
      DayFeed f = feed;
      return f == null ? Optional.empty() : f.latest(contract);
    };
  }

  /** The session the engine is running, if any. */
  public synchronized Optional<LocalDate> day() {
    return Optional.ofNullable(day);
  }

  /** Each of today's books: "running", or why it stopped. */
  public synchronized Map<String, String> books() {
    Map<String, String> out = new LinkedHashMap<>();
    books.forEach(
        (id, r) ->
            out.put(id, r.failure().get() == null ? "running" : "stopped: " + r.failure().get()));
    return out;
  }

  public synchronized boolean finished() {
    return finished;
  }

  public synchronized Optional<String> lastProblem() {
    return lastProblem;
  }

  /** Today's book ids, in the order they run. */
  public synchronized List<String> bookIds() {
    return new ArrayList<>(books.keySet());
  }
}
