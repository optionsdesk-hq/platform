package com.optionsdesk.trading.journal.repository;

import com.optionsdesk.trading.journal.model.Book;
import java.util.List;
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
}
