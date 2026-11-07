package com.optionsdesk.trading.book.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.optionsdesk.shared.calendar.MarketCalendar;
import com.optionsdesk.shared.domain.Right;
import com.optionsdesk.trading.book.spi.BookFactory;
import com.optionsdesk.trading.book.spi.ContractLookup;
import com.optionsdesk.trading.book.spi.DayFeed;
import com.optionsdesk.trading.book.spi.MinuteBook;
import com.optionsdesk.trading.strategy.model.MinuteMarket;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MinuteEngineTest {

  static final LocalDate MON = LocalDate.of(2026, 11, 9);
  static final LocalDate HOLIDAY = LocalDate.of(2026, 11, 10);
  static final MarketCalendar CALENDAR = new MarketCalendar(Set.of(HOLIDAY));

  static ZonedDateTime at(LocalDate d, int h, int m, int s) {
    return d.atTime(h, m, s).atZone(MarketCalendar.IST);
  }

  /** A feed that records captures, and can be told to fail. */
  static final class FakeFeed implements DayFeed {
    final List<LocalTime> captured = new ArrayList<>();
    boolean failNext;

    @Override
    public void capture(LocalTime minute) {
      if (failNext) {
        failNext = false;
        throw new IllegalStateException("no quotes");
      }
      captured.add(minute);
    }

    @Override
    public ContractLookup contracts() {
      return (k, r) -> Optional.empty();
    }

    @Override
    public List<LocalTime> minutes() {
      return captured;
    }

    @Override
    public OptionalDouble spot(LocalTime minute) {
      return OptionalDouble.of(24_000);
    }

    @Override
    public Optional<Snapshot> quote(double strike, Right right, LocalTime minute) {
      return Optional.empty();
    }
  }

  /** A book that records what it was told, and can be made to throw at a minute. */
  static final class Recorder implements MinuteBook {
    final String id;
    final List<String> calls = new ArrayList<>();
    LocalTime throwAt;

    Recorder(String id) {
      this.id = id;
    }

    @Override
    public String id() {
      return id;
    }

    @Override
    public void onMinute(LocalTime minute, MinuteMarket market) {
      if (minute.equals(throwAt)) {
        throw new IllegalStateException("boom");
      }
      calls.add(minute.toString());
    }

    @Override
    public void finish(MinuteMarket market) {
      calls.add("finish");
    }

    @Override
    public String summary() {
      return "made " + calls.size() + " calls";
    }

    @Override
    public void exitNow(MinuteMarket market, String reason) {
      calls.add("exit:" + reason);
    }
  }

  final List<FakeFeed> feeds = new ArrayList<>();
  final List<Recorder> books = new ArrayList<>();
  final List<LocalDate> opened = new ArrayList<>();
  final List<String> sent = new ArrayList<>();
  final com.optionsdesk.trading.alerts.port.Alerts alerts =
      new com.optionsdesk.trading.alerts.port.Alerts() {
        @Override
        public void info(String text) {
          sent.add("info: " + text);
        }

        @Override
        public void urgent(String text) {
          sent.add("URGENT: " + text);
        }
      };
  boolean feedDown;

  MinuteEngine engine(BookFactory... extra) {
    DayFeed.Factory factory =
        d -> {
          if (feedDown) {
            throw new IllegalStateException("no broker session");
          }
          opened.add(d);
          var f = new FakeFeed();
          feeds.add(f);
          return f;
        };
    List<BookFactory> factories = new ArrayList<>(List.of(extra));
    factories.add(
        (d, c) -> {
          var r = new Recorder("a-" + d);
          books.add(r);
          return Optional.of(r);
        });
    return new MinuteEngine(CALENDAR, factory, factories, id -> Runnable::run, alerts);
  }

  @Test
  void runsTheSessionMinuteByMinuteThenFinishesOnce() {
    var e = engine();
    e.tick(at(MON, 9, 14, 2)); // before the open
    assertThat(opened).isEmpty();
    e.tick(at(MON, 9, 15, 2));
    e.tick(at(MON, 9, 16, 2));
    e.tick(at(MON, 15, 30, 2));
    e.tick(at(MON, 15, 31, 2));
    e.tick(at(MON, 15, 32, 2));
    assertThat(opened).containsExactly(MON);
    assertThat(books.get(0).calls).containsExactly("09:15", "09:16", "15:30", "finish");
    assertThat(e.finished()).isTrue();
    assertThat(e.day()).contains(MON);
    assertThat(e.books()).containsEntry("a-" + MON, "running");
  }

  @Test
  void holidaysAndWeekendsDoNothing() {
    var e = engine();
    e.tick(at(HOLIDAY, 10, 0, 2));
    e.tick(at(LocalDate.of(2026, 11, 14), 10, 0, 2)); // Saturday
    assertThat(opened).isEmpty();
    assertThat(e.day()).isEmpty();
  }

  @Test
  void aFailedCaptureSkipsTheMinuteForEveryBook() {
    var e = engine();
    e.tick(at(MON, 9, 15, 2));
    feeds.get(0).failNext = true;
    e.tick(at(MON, 9, 16, 2));
    e.tick(at(MON, 9, 17, 2));
    assertThat(books.get(0).calls).containsExactly("09:15", "09:17");
    assertThat(e.lastProblem())
        .hasValueSatisfying(p -> assertThat(p).contains("09:16 capture failed"));
  }

  @Test
  void aThrowingBookStopsForTheDayAndTheOthersCarryOn() {
    var bad = new Recorder("bad");
    bad.throwAt = LocalTime.of(9, 16);
    var e = engine((d, c) -> Optional.of(bad));
    e.tick(at(MON, 9, 15, 2));
    e.tick(at(MON, 9, 16, 2));
    e.tick(at(MON, 9, 17, 2));
    e.tick(at(MON, 15, 31, 2));
    assertThat(bad.calls).containsExactly("09:15");
    assertThat(books.get(0).calls).containsExactly("09:15", "09:16", "09:17", "finish");
    assertThat(e.books()).hasEntrySatisfying("bad", s -> assertThat(s).startsWith("stopped"));
    assertThat(e.bookIds()).containsExactly("bad", "a-" + MON);
  }

  @Test
  void aFeedThatCantOpenIsRetriedNextMinuteAndNeverOpensAfterTheClose() {
    var e = engine();
    feedDown = true;
    e.tick(at(MON, 9, 15, 2));
    assertThat(e.lastProblem()).hasValueSatisfying(p -> assertThat(p).contains("feed not open"));
    feedDown = false;
    e.tick(at(MON, 9, 16, 2));
    assertThat(opened).containsExactly(MON);
    assertThat(books.get(0).calls).containsExactly("09:16");

    var late = engine();
    late.tick(at(LocalDate.of(2026, 11, 11), 15, 45, 2));
    assertThat(late.day()).isEmpty();
  }

  @Test
  void eachSessionGetsFreshBooks() {
    var e = engine();
    e.tick(at(MON, 9, 15, 2));
    e.tick(at(MON, 15, 31, 2));
    var wed = LocalDate.of(2026, 11, 11);
    e.tick(at(wed, 9, 15, 2));
    assertThat(opened).containsExactly(MON, wed);
    assertThat(books).hasSize(2);
    assertThat(books.get(1).calls).containsExactly("09:15");
    assertThat(e.finished()).isFalse();
  }

  @Test
  void exitClosesOnTheBooksThreadAndStopsItForTheDay() {
    var e = engine();
    e.tick(at(MON, 9, 15, 2));
    assertThat(e.exit("a-" + MON, "owner")).isTrue();
    e.tick(at(MON, 9, 16, 2));
    e.tick(at(MON, 15, 31, 2));
    assertThat(books.get(0).calls).containsExactly("09:15", "exit:owner");
    assertThat(e.books()).containsEntry("a-" + MON, "stopped: exited: owner");
    assertThat(e.exit("a-" + MON, "again")).isFalse(); // already stopped
    assertThat(e.exit("nope", "x")).isFalse();
  }

  @Test
  void exitAllAsksEveryRunningBookAndABookThatCantExitIsReported() {
    var plain =
        new MinuteBook() {
          @Override
          public String id() {
            return "plain";
          }

          @Override
          public void onMinute(LocalTime minute, MinuteMarket market) {}

          @Override
          public void finish(MinuteMarket market) {}
        };
    var e = engine((d, c) -> Optional.of(plain));
    assertThat(e.exitAll("kill")).isEmpty(); // nothing open before the first tick
    e.tick(at(MON, 9, 15, 2));
    assertThat(e.exitAll("kill")).containsExactly("plain", "a-" + MON);
    assertThat(e.lastProblem()).hasValueSatisfying(p -> assertThat(p).contains("can't exit"));
  }

  @Test
  void alertsTheOpenEachProblemOnceADayAndTheSummaries() {
    var e = engine();
    e.tick(at(MON, 9, 15, 2));
    for (int m = 16; m < 20; m++) {
      feeds.get(0).failNext = true;
      e.tick(at(MON, 9, m, 2)); // four failed captures
    }
    e.tick(at(MON, 15, 31, 2));
    assertThat(sent)
        .containsExactly(
            "info: session " + MON + " opened: books [a-" + MON + "]",
            "URGENT: 09:16 capture failed, minute skipped: no quotes",
            "info: a-" + MON + ": made 2 calls");
  }
}
