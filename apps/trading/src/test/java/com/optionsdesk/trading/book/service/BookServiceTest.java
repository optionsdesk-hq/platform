package com.optionsdesk.trading.book.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.optionsdesk.trading.journal.model.Book;
import com.optionsdesk.trading.journal.repository.BookRepository;
import com.optionsdesk.trading.journal.repository.PositionJournal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

class BookServiceTest {

  static final Book A = new Book("book-a", "s", "PAPER", 1, "ACTIVE");
  static final Book B = new Book("book-b", "s", "PAPER", 1, "PAUSED");
  static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-09T06:00:00Z"), ZoneOffset.UTC);

  final BookRepository books = mock(BookRepository.class);
  final PositionJournal positions = mock(PositionJournal.class);
  final MinuteEngine engine = mock(MinuteEngine.class);

  BookService service(boolean engineOn) {
    var beans = new StaticListableBeanFactory();
    if (engineOn) {
      beans.addBean("engine", engine);
    }
    return new BookService(books, positions, beans.getBeanProvider(MinuteEngine.class), CLOCK);
  }

  @Test
  void booksCombineTheJournalAndTheEngine() {
    when(books.findAll()).thenReturn(List.of(A, B));
    when(engine.books()).thenReturn(Map.of("book-a", "running"));
    when(positions.countOpen("book-a")).thenReturn(2L);
    // the IST day starts at 18:30 UTC the evening before
    when(positions.realisedSince(eq("book-a"), eq(Instant.parse("2026-11-08T18:30:00Z"))))
        .thenReturn(500.0);
    var out = service(true).books();
    assertThat(out).hasSize(2);
    assertThat(out.get(0).today()).isEqualTo("running");
    assertThat(out.get(0).openPositions()).isEqualTo(2);
    assertThat(out.get(0).realisedToday()).isEqualTo(500.0);
    assertThat(out.get(1).today()).isEqualTo("not running");
    assertThat(service(false).books().get(0).today()).isEqualTo("not running");
  }

  @Test
  void positionsOfAnUnknownBookAreEmptyAndLimitsAreBounded() {
    when(books.find("nope")).thenReturn(Optional.empty());
    assertThat(service(true).positions("nope", Optional.empty(), 10)).isEmpty();
    when(books.find("book-a")).thenReturn(Optional.of(A));
    service(true).positions("book-a", Optional.empty(), 100_000);
    verify(positions).recent("book-a", 500);
    service(true).positions("book-a", Optional.of("open"), 10);
    verify(positions).find("book-a", "OPEN");
  }

  @Test
  void exitPausesAndAsksTheEngineToClose() {
    when(books.find("book-a")).thenReturn(Optional.of(A));
    when(engine.exit("book-a", "why")).thenReturn(true);
    var out = service(true).exit("book-a", "why");
    assertThat(out.done()).isTrue();
    assertThat(out.message()).contains("exiting now");
    verify(books).setState("book-a", "PAUSED");

    when(engine.exit("book-a", "why")).thenReturn(false);
    assertThat(service(true).exit("book-a", "why").message()).contains("isn't running today");
    assertThat(service(false).exit("book-a", "why").done()).isTrue();

    when(books.find("nope")).thenReturn(Optional.empty());
    assertThat(service(true).exit("nope", "why").done()).isFalse();
    verify(books, never()).setState(eq("nope"), any());
  }

  @Test
  void theKillSwitchPausesEverythingAndExitsWhatRuns() {
    when(books.findAll()).thenReturn(List.of(A, B));
    when(engine.exitAll("kill")).thenReturn(List.of("book-a"));
    var out = service(true).killAll("kill");
    assertThat(out.message()).contains("book-a");
    verify(books).setState("book-a", "PAUSED");
    verify(books).setState("book-b", "PAUSED");
    assertThat(service(false).killAll("kill").message()).contains("none running");
  }

  @Test
  void pauseAndResumeChangeTheJournalState() {
    when(books.setState("book-a", "PAUSED")).thenReturn(true);
    when(books.setState("book-a", "ACTIVE")).thenReturn(true);
    assertThat(service(true).pause("book-a").done()).isTrue();
    assertThat(service(true).resume("book-a").message()).contains("next session");
    assertThat(service(true).pause("nope").done()).isFalse();
  }

  @Test
  void engineViewWhenOnOrOff() {
    when(engine.day()).thenReturn(Optional.empty());
    when(engine.books()).thenReturn(Map.of());
    when(engine.lastProblem()).thenReturn(Optional.empty());
    assertThat(service(true).engine()).isPresent();
    assertThat(service(false).engine()).isEmpty();
  }
}
