package com.optionsdesk.trading.journal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.optionsdesk.shared.domain.OptionContract;
import com.optionsdesk.shared.domain.Right;
import com.optionsdesk.trading.PostgresTestConfiguration;
import com.optionsdesk.trading.execution.Execution;
import com.optionsdesk.trading.execution.OrderRequest;
import com.optionsdesk.trading.journal.BookRepository.Book;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.testcontainers.junit.jupiter.Testcontainers;

/** A spread opened and closed through the journal on a real Postgres: legs, orders, fills, P&L. */
@SpringBootTest
@Import(PostgresTestConfiguration.class)
@Testcontainers(disabledWithoutDocker = true)
class PositionJournalTest {

  static final LocalDate EXP = LocalDate.of(2026, 11, 17);
  static final OptionContract SHORT =
      new OptionContract("NIFTY26N1724500PE", "NIFTY", EXP, 24500, Right.PE, 65);
  static final OptionContract WING =
      new OptionContract("NIFTY26N1724200PE", "NIFTY", EXP, 24200, Right.PE, 65);
  static final Instant T0 = Instant.parse("2026-11-09T09:50:00Z");

  @Autowired PositionJournal journal;
  @Autowired BookRepository books;
  @Autowired JdbcClient jdbc;

  static int tag;

  static Execution fill(
      OptionContract c, OrderRequest.Side side, double px, double charges, Instant at) {
    return new Execution(new OrderRequest(c, side, 130, "t" + tag++), px, 130, charges, at, "test");
  }

  @BeforeEach
  void book() {
    books.save(new Book("spread-a", "credit-spread", "PAPER", 2, "ACTIVE"));
  }

  @Test
  void openThenCloseRecordsEverythingAndTheRealisedPnl() {
    long id =
        journal.opened(
            "spread-a",
            List.of(
                fill(WING, OrderRequest.Side.BUY, 20, 30, T0),
                fill(SHORT, OrderRequest.Side.SELL, 60, 40, T0)),
            Optional.of("RBI policy"),
            Optional.empty());
    // the database is shared by the tests in this class: look for this position, not a count
    var p =
        journal.find("spread-a", "OPEN").stream()
            .filter(x -> x.id() == id)
            .findFirst()
            .orElseThrow();
    assertThat(p.credit()).isEqualTo((60 - 20) * 130); // 5,200
    assertThat(p.costs()).isEqualTo(70);
    assertThat(p.legs())
        .extracting(l -> l.contract().tradingSymbol() + " " + l.lots() + " @ " + l.entryPrice())
        .containsExactly("NIFTY26N1724200PE 2 @ 20.0", "NIFTY26N1724500PE -2 @ 60.0");

    journal.closed(
        id,
        List.of(
            fill(SHORT, OrderRequest.Side.BUY, 25, 35, T0.plusSeconds(3600)),
            fill(WING, OrderRequest.Side.SELL, 5, 25, T0.plusSeconds(3600))),
        "profit target");
    var closed = journal.get(id).orElseThrow();
    assertThat(closed.status()).isEqualTo("CLOSED");
    // 5,200 credit − (25 − 5) × 130 to close − 130 charges
    assertThat(closed.realisedPnl())
        .hasValueSatisfying(v -> assertThat(v).isCloseTo(2_470, within(1e-9)));
    assertThat(closed.exitReason()).contains("profit target");
    assertThat(jdbc.sql("SELECT count(*) FROM fill").query(Long.class).single())
        .isGreaterThanOrEqualTo(4);
    assertThat(
            jdbc.sql("SELECT count(*) FROM leg WHERE position_id = :id AND exit_price IS NULL")
                .param("id", id)
                .query(Long.class)
                .single())
        .isZero();
  }

  @Test
  void aFailedOpenKeepsTheUnwindAndItsCost() {
    long id =
        journal.failed(
            "spread-a",
            List.of(fill(WING, OrderRequest.Side.BUY, 20, 30, T0)),
            List.of(fill(WING, OrderRequest.Side.SELL, 19.5, 30, T0.plusSeconds(1))),
            "SELL 130 NIFTY26N1724500PE: no quote");
    var p = journal.get(id).orElseThrow();
    assertThat(p.status()).isEqualTo("FAILED");
    assertThat(p.realisedPnl())
        .hasValueSatisfying(v -> assertThat(v).isCloseTo(-0.5 * 130 - 60, within(1e-9)));
    assertThat(p.exitReason()).hasValueSatisfying(r -> assertThat(r).contains("no quote"));
  }

  @Test
  void aCloseMustMatchTheOpenLegs() {
    long id =
        journal.opened(
            "spread-a",
            List.of(fill(WING, OrderRequest.Side.BUY, 20, 30, T0)),
            Optional.empty(),
            Optional.empty());
    assertThatThrownBy(
            () -> journal.closed(id, List.of(fill(SHORT, OrderRequest.Side.BUY, 1, 1, T0)), "x"))
        .hasMessageContaining("no open leg");
    assertThat(journal.get(id).orElseThrow().status()).isEqualTo("OPEN"); // rolled back
    assertThatThrownBy(
            () -> journal.opened("spread-a", List.of(), Optional.empty(), Optional.empty()))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
