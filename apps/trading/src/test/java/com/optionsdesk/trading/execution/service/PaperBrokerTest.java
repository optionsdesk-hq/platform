package com.optionsdesk.trading.execution.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.optionsdesk.shared.domain.OptionContract;
import com.optionsdesk.shared.domain.Quote;
import com.optionsdesk.shared.domain.Right;
import com.optionsdesk.shared.money.Charges;
import com.optionsdesk.trading.execution.model.OrderRequest;
import com.optionsdesk.trading.execution.port.Broker;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PaperBrokerTest {

  static final Instant NOW = Instant.parse("2026-11-09T09:50:00Z");
  static final Charges CHARGES = new Charges(20.0, 0.1, 0.03503, 10.0, 0.003, 18.0, 2, 0.05);
  static final OptionContract CE =
      new OptionContract(
          "NIFTY26N1724600CE", "NIFTY", LocalDate.of(2026, 11, 17), 24600, Right.CE, 65);

  final Map<OptionContract, Quote> quotes = new HashMap<>();
  final PaperBroker broker =
      new PaperBroker(
          c -> Optional.ofNullable(quotes.get(c)),
          CHARGES,
          Clock.fixed(NOW, ZoneOffset.UTC),
          Duration.ofSeconds(5));

  void quote(double ltp, double bid, double ask, Instant at) {
    quotes.put(CE, new Quote(CE, ltp, bid, ask, at));
  }

  static OrderRequest order(OrderRequest.Side side, String tag) {
    return new OrderRequest(CE, side, 65, tag);
  }

  @Test
  void sellsAtTheBidAndBuysAtTheAskWithRealCharges() throws Exception {
    quote(101, 100.5, 101.5, NOW);
    var sell = broker.execute(order(OrderRequest.Side.SELL, "t1"));
    var buy = broker.execute(order(OrderRequest.Side.BUY, "t2"));
    assertThat(sell.price()).isEqualTo(100.5);
    assertThat(sell.source()).isEqualTo("bid");
    assertThat(buy.price()).isEqualTo(101.5);
    assertThat(sell.charges()).isCloseTo(CHARGES.order(100.5 * 65, true), within(1e-9));
    assertThat(buy.charges()).isCloseTo(CHARGES.order(101.5 * 65, false), within(1e-9));
    assertThat(sell.cashFlow()).isEqualTo(100.5 * 65);
    assertThat(buy.cashFlow()).isEqualTo(-101.5 * 65);
  }

  @Test
  void anEmptySideFillsAtTheMidMovedAgainstUs() throws Exception {
    quote(101, 0, 0, NOW);
    var sell = broker.execute(order(OrderRequest.Side.SELL, "t1"));
    var buy = broker.execute(order(OrderRequest.Side.BUY, "t2"));
    assertThat(sell.price()).isEqualTo(100.9); // 2 ticks of 0.05
    assertThat(buy.price()).isEqualTo(101.1);
    assertThat(sell.source()).isEqualTo("mid-slipped");
  }

  @Test
  void refusesToInventAFill() {
    assertThatThrownBy(() -> broker.execute(order(OrderRequest.Side.SELL, "t1")))
        .hasMessageContaining("no quote");
    quote(101, 100.5, 101.5, NOW.minusSeconds(6));
    assertThatThrownBy(() -> broker.execute(order(OrderRequest.Side.SELL, "t2")))
        .hasMessageContaining("older than");
    quote(0, 0, 0, NOW);
    assertThatThrownBy(() -> broker.execute(order(OrderRequest.Side.SELL, "t3")))
        .hasMessageContaining("no bid, ask or last trade");
  }

  @Test
  void aTagFillsOnce() throws Exception {
    quote(101, 100.5, 101.5, NOW);
    broker.execute(order(OrderRequest.Side.SELL, "t1"));
    assertThatThrownBy(() -> broker.execute(order(OrderRequest.Side.SELL, "t1")))
        .isInstanceOf(Broker.OrderRejected.class)
        .hasMessageContaining("duplicate tag");
  }

  @Test
  void ordersAreWholeLots() {
    assertThatThrownBy(() -> new OrderRequest(CE, OrderRequest.Side.BUY, 64, "x"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new OrderRequest(CE, OrderRequest.Side.BUY, 0, "x"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
