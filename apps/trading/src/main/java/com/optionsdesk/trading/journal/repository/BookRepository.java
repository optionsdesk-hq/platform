package com.optionsdesk.trading.journal.repository;

import com.optionsdesk.trading.journal.model.Book;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Books (one per strategy variant). Plain SQL through {@link JdbcClient}: explicit and easy to
 * review.
 */
@Repository
public class BookRepository {

  private final JdbcClient jdbc;

  BookRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  public void save(Book book) {
    jdbc.sql(
            """
            INSERT INTO book (id, strategy_id, mode, lots, state) VALUES (:id, :strategy, :mode, :lots, :state)
            ON CONFLICT (id) DO UPDATE SET lots = EXCLUDED.lots, state = EXCLUDED.state
            """)
        .param("id", book.id())
        .param("strategy", book.strategyId())
        .param("mode", book.mode())
        .param("lots", book.lots())
        .param("state", book.state())
        .update();
  }

  public List<Book> findAll() {
    return jdbc.sql("SELECT id, strategy_id, mode, lots, state FROM book ORDER BY id")
        .query(Book.class)
        .list();
  }

  public Optional<Book> find(String id) {
    return jdbc.sql("SELECT id, strategy_id, mode, lots, state FROM book WHERE id = :id")
        .param("id", id)
        .query(Book.class)
        .optional();
  }

  /** Sets a book's state (ACTIVE, PAUSED, KILLED); false when there is no such book. */
  public boolean setState(String id, String state) {
    return jdbc.sql("UPDATE book SET state = :state WHERE id = :id")
            .param("id", id)
            .param("state", state)
            .update()
        == 1;
  }
}
