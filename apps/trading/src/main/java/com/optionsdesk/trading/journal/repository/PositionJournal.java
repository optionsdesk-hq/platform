package com.optionsdesk.trading.journal.repository;

import com.optionsdesk.shared.domain.Leg;
import com.optionsdesk.shared.domain.OptionContract;
import com.optionsdesk.shared.domain.Right;
import com.optionsdesk.trading.execution.model.Execution;
import com.optionsdesk.trading.journal.model.Position;
import com.optionsdesk.trading.journal.port.Ledger;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Positions with their legs, orders and fills. Every change is one transaction: a position is never
 * half-recorded. Realised P&L = credit (cash in at entry, negative for a debit) + cash at exit −
 * all charges.
 */
@Repository
public class PositionJournal implements Ledger {

  private final JdbcClient jdbc;

  PositionJournal(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  /** Record an opened position from its fills; returns its id. */
  @Override
  @Transactional
  public long opened(
      String bookId, List<Execution> fills, Optional<String> eventTag, Optional<Long> signalId) {
    if (fills.isEmpty()) {
      throw new IllegalArgumentException("an opened position needs fills");
    }
    return insert(bookId, "OPEN", fills, eventTag, signalId, Optional.empty());
  }

  /**
   * Record an open that failed: the legs that filled, the unwind that closed them, and why. Status
   * FAILED; realised P&L counts both sides (usually the spread and charges lost).
   */
  @Override
  @Transactional
  public long failed(String bookId, List<Execution> fills, List<Execution> unwind, String reason) {
    long id =
        insert(bookId, "FAILED", fills, Optional.empty(), Optional.empty(), Optional.of(reason));
    if (!fills.isEmpty()) {
      exits(id, unwind);
      settle(id, unwind, "FAILED", reason);
    }
    return id;
  }

  /** Record a position's close from the closing fills. */
  @Override
  @Transactional
  public void closed(long positionId, List<Execution> fills, String reason) {
    exits(positionId, fills);
    settle(positionId, fills, "CLOSED", reason);
  }

  /** Positions of a book in a status, oldest first. */
  public List<Position> find(String bookId, String status) {
    var ids =
        jdbc.sql("SELECT id FROM position WHERE book_id = :book AND status = :status ORDER BY id")
            .param("book", bookId)
            .param("status", status)
            .query(Long.class)
            .list();
    return ids.stream().map(this::get).flatMap(Optional::stream).toList();
  }

  public Optional<Position> get(long id) {
    var legs =
        jdbc.sql(
                """
                SELECT trading_symbol, underlying, expiry, strike, opt_right, lots, lot_size, entry_price
                FROM leg WHERE position_id = :id ORDER BY id
                """)
            .param("id", id)
            .query(
                (rs, n) ->
                    new Leg(
                        new OptionContract(
                            rs.getString("trading_symbol"),
                            rs.getString("underlying"),
                            rs.getDate("expiry").toLocalDate(),
                            rs.getDouble("strike"),
                            Right.valueOf(rs.getString("opt_right")),
                            rs.getInt("lot_size")),
                        rs.getInt("lots"),
                        rs.getDouble("entry_price")))
            .list();
    return jdbc.sql(
            "SELECT id, book_id, status, credit, costs, realised_pnl, exit_reason FROM position WHERE id = :id")
        .param("id", id)
        .query(
            (rs, n) ->
                new Position(
                    rs.getLong("id"),
                    rs.getString("book_id"),
                    rs.getString("status"),
                    legs,
                    rs.getDouble("credit"),
                    rs.getDouble("costs"),
                    Optional.ofNullable(rs.getBigDecimal("realised_pnl"))
                        .map(java.math.BigDecimal::doubleValue),
                    Optional.ofNullable(rs.getString("exit_reason"))))
        .optional();
  }

  private long insert(
      String bookId,
      String status,
      List<Execution> fills,
      Optional<String> eventTag,
      Optional<Long> signalId,
      Optional<String> reason) {
    double credit = fills.stream().mapToDouble(Execution::cashFlow).sum();
    double costs = fills.stream().mapToDouble(Execution::charges).sum();
    var key = new GeneratedKeyHolder();
    jdbc.sql(
            """
            INSERT INTO position (book_id, status, opened_at, credit, costs, exit_reason, event_tag, signal_id)
            VALUES (:book, :status, :at, :credit, :costs, :reason, :event, :signal)
            """)
        .param("book", bookId)
        .param("status", status)
        .param("at", fills.isEmpty() ? null : Timestamp.from(fills.get(0).at()))
        .param("credit", round2(credit))
        .param("costs", round2(costs))
        .param("reason", reason.orElse(null))
        .param("event", eventTag.orElse(null))
        .param("signal", signalId.orElse(null))
        .update(key, "id");
    long id = Objects.requireNonNull(key.getKey()).longValue();
    for (Execution f : fills) {
      var c = f.request().contract();
      int lots = f.quantity() / c.lotSize() * (f.request().isSell() ? -1 : 1);
      var legKey = new GeneratedKeyHolder();
      jdbc.sql(
              """
              INSERT INTO leg (position_id, trading_symbol, underlying, expiry, strike, opt_right, lots, lot_size,
                               entry_price)
              VALUES (:pos, :sym, :und, :exp, :k, :r, :lots, :size, :px)
              """)
          .param("pos", id)
          .param("sym", c.tradingSymbol())
          .param("und", c.underlying())
          .param("exp", c.expiry())
          .param("k", c.strike())
          .param("r", c.right().name())
          .param("lots", lots)
          .param("size", c.lotSize())
          .param("px", f.price())
          .update(legKey, "id");
      order(Objects.requireNonNull(legKey.getKey()).longValue(), f);
    }
    return id;
  }

  private void exits(long positionId, List<Execution> fills) {
    Map<String, Long> legBySymbol = new HashMap<>();
    jdbc.sql("SELECT id, trading_symbol FROM leg WHERE position_id = :id AND exit_price IS NULL")
        .param("id", positionId)
        .query((rs, n) -> legBySymbol.putIfAbsent(rs.getString("trading_symbol"), rs.getLong("id")))
        .list();
    for (Execution f : fills) {
      Long leg = legBySymbol.remove(f.request().contract().tradingSymbol());
      if (leg == null) {
        throw new IllegalStateException(
            "position %d has no open leg in %s"
                .formatted(positionId, f.request().contract().tradingSymbol()));
      }
      jdbc.sql("UPDATE leg SET exit_price = :px WHERE id = :id")
          .param("px", f.price())
          .param("id", leg)
          .update();
      order(leg, f);
    }
  }

  private void settle(long positionId, List<Execution> fills, String status, String reason) {
    var at = fills.isEmpty() ? null : Timestamp.from(fills.get(fills.size() - 1).at());
    double cash = fills.stream().mapToDouble(Execution::cashFlow).sum();
    double charges = fills.stream().mapToDouble(Execution::charges).sum();
    record Entry(double credit, double costs) {}
    var entry =
        jdbc.sql("SELECT credit, costs FROM position WHERE id = :id")
            .param("id", positionId)
            .query((rs, n) -> new Entry(rs.getDouble("credit"), rs.getDouble("costs")))
            .single();
    double costs = entry.costs() + charges;
    double pnl = entry.credit() + cash - costs;
    jdbc.sql(
            """
            UPDATE position SET status = :status, closed_at = :at, costs = :costs, realised_pnl = :pnl,
                                exit_reason = :reason
            WHERE id = :id
            """)
        .param("status", status)
        .param("at", at)
        .param("costs", round2(costs))
        .param("pnl", round2(pnl))
        .param("reason", reason)
        .param("id", positionId)
        .update();
  }

  private void order(long legId, Execution f) {
    var key = new GeneratedKeyHolder();
    jdbc.sql(
            """
            INSERT INTO "order" (leg_id, side, quantity, type, state, tag, created_at)
            VALUES (:leg, :side, :qty, 'MARKET', 'FILLED', :tag, :at)
            """)
        .param("leg", legId)
        .param("side", f.request().side().name())
        .param("qty", f.quantity())
        .param("tag", f.request().tag())
        .param("at", Timestamp.from(f.at()))
        .update(key, "id");
    jdbc.sql(
            "INSERT INTO fill (order_id, price, quantity, charges, at) VALUES (:o, :px, :qty, :ch, :at)")
        .param("o", Objects.requireNonNull(key.getKey()).longValue())
        .param("px", f.price())
        .param("qty", f.quantity())
        .param("ch", round2(f.charges()))
        .param("at", Timestamp.from(f.at()))
        .update();
  }

  private static double round2(double x) {
    return Math.round(x * 100) / 100.0;
  }
}
