package com.optionsdesk.trading.api.dto;

import com.optionsdesk.shared.domain.Leg;
import com.optionsdesk.trading.book.service.BookService;
import com.optionsdesk.trading.journal.model.Position;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * The API's response shapes. The API never returns journal rows or domain objects directly: these
 * views decide what is shown, so the database can change without breaking clients.
 */
public final class ApiViews {

  private ApiViews() {}

  /** A book and its day. {@code today}: "running", "stopped: …", "exited: …" or "not running". */
  public record BookView(
      String id,
      String strategy,
      String mode,
      int lots,
      String state,
      String today,
      long openPositions,
      double realisedToday) {

    public static BookView of(BookService.BookStatus s) {
      var b = s.book();
      return new BookView(
          b.id(),
          b.strategyId(),
          b.mode(),
          b.lots(),
          b.state(),
          s.today(),
          s.openPositions(),
          round2(s.realisedToday()));
    }
  }

  /** One leg of a position. */
  public record LegView(
      String symbol, String right, double strike, LocalDate expiry, int lots, double entry) {

    static LegView of(Leg l) {
      var c = l.contract();
      return new LegView(
          c.tradingSymbol(), c.right().name(), c.strike(), c.expiry(), l.lots(), l.entryPrice());
    }
  }

  /** A position with its legs; realised P&L and exit reason once closed. */
  public record PositionView(
      long id,
      String book,
      String status,
      double credit,
      double costs,
      Double realisedPnl,
      String exitReason,
      List<LegView> legs) {

    public static PositionView of(Position p) {
      return new PositionView(
          p.id(),
          p.bookId(),
          p.status(),
          p.credit(),
          p.costs(),
          p.realisedPnl().orElse(null),
          p.exitReason().orElse(null),
          p.legs().stream().map(LegView::of).toList());
    }
  }

  /** The minute engine: on or off, today's session and each book's state. */
  public record EngineView(
      boolean on, LocalDate session, boolean finished, Map<String, String> books, String problem) {

    public static EngineView off() {
      return new EngineView(false, null, false, Map.of(), null);
    }

    public static EngineView of(BookService.EngineStatus e) {
      return new EngineView(
          true, e.day().orElse(null), e.finished(), e.books(), e.lastProblem().orElse(null));
    }
  }

  /** What an owner action did. */
  public record ActionView(boolean done, String message) {

    public static ActionView of(BookService.ActionOutcome o) {
      return new ActionView(o.done(), o.message());
    }
  }

  private static double round2(double x) {
    return Math.round(x * 100) / 100.0;
  }
}
