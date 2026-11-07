package com.optionsdesk.trading.journal.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.optionsdesk.trading.PostgresTestConfiguration;
import com.optionsdesk.trading.journal.model.Book;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Flyway applies V1 to a real Postgres; the schema's constraints reject bad data. */
@SpringBootTest
@Import(PostgresTestConfiguration.class)
@Testcontainers(disabledWithoutDocker = true)
class JournalSchemaTest {

  @Autowired BookRepository books;
  @Autowired JdbcClient jdbc;

  @Test
  void migrationsCreateTheJournal() {
    var tables =
        jdbc.sql("SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'")
            .query(String.class)
            .list();
    assertThat(tables)
        .contains(
            "book", "position", "leg", "order", "fill", "mark", "signal", "audit_event", "job_run");
  }

  @Test
  void booksRoundTripAndUpsert() {
    books.save(new Book("spread-a", "credit-spread", "PAPER", 2, "ACTIVE"));
    books.save(new Book("spread-a", "credit-spread", "PAPER", 2, "PAUSED"));
    assertThat(books.findAll())
        .containsExactly(new Book("spread-a", "credit-spread", "PAPER", 2, "PAUSED"));
  }

  @Test
  void constraintsRejectImpossibleRows() {
    assertThatThrownBy(() -> books.save(new Book("x", "s", "REAL_MONEY", 1, "ACTIVE")))
        .hasMessageContaining("book_mode_check");
    assertThatThrownBy(() -> books.save(new Book("y", "s", "PAPER", 0, "ACTIVE")))
        .hasMessageContaining("book_lots_check");
  }
}
