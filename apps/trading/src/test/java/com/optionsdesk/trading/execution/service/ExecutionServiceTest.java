package com.optionsdesk.trading.execution.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.optionsdesk.shared.domain.Leg;
import com.optionsdesk.shared.domain.OptionContract;
import com.optionsdesk.shared.domain.Right;
import com.optionsdesk.trading.execution.model.Execution;
import com.optionsdesk.trading.execution.model.OrderRequest;
import com.optionsdesk.trading.execution.port.Broker;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Leg order and unwinds, on a broker that fills everything at 10 except the symbols told to reject.
 */
class ExecutionServiceTest {

  static final LocalDate EXP = LocalDate.of(2026, 11, 17);

  static OptionContract c(double k, Right r) {
    return new OptionContract("N" + (int) k + r, "NIFTY", EXP, k, r, 65);
  }

  /** A bull put spread at 2 lots: short 24500 PE, long 24200 PE. */
  static final List<Leg> SPREAD =
      List.of(new Leg(c(24500, Right.PE), -2, 0), new Leg(c(24200, Right.PE), 2, 0));

  static final class ScriptedBroker implements Broker {
    final List<String> sent = new ArrayList<>();
    final Set<String> reject = new HashSet<>();

    @Override
    public Execution execute(OrderRequest r) throws OrderRejected {
      String what = r.side() + " " + r.contract().tradingSymbol();
      if (reject.contains(what)) {
        throw new OrderRejected(r, "test rejection");
      }
      sent.add(what + " " + r.quantity() + " " + r.tag());
      return new Execution(r, 10, r.quantity(), 1, Instant.EPOCH, "test");
    }
  }

  final ScriptedBroker broker = new ScriptedBroker();
  final ExecutionService service = new ExecutionService(broker);

  @Test
  void opensTheWingBeforeTheShort() {
    var out = service.open("p1", SPREAD);
    assertThat(out.ok()).isTrue();
    assertThat(broker.sent).containsExactly("BUY N24200PE 130 p1-o0", "SELL N24500PE 130 p1-o1");
    assertThat(out.cashFlow()).isZero(); // 10 in, 10 out
    assertThat(out.charges()).isEqualTo(2);
  }

  @Test
  void closesTheShortBeforeTheWing() {
    var out = service.close("p1", SPREAD);
    assertThat(out.ok()).isTrue();
    assertThat(broker.sent).containsExactly("BUY N24500PE 130 p1-c0", "SELL N24200PE 130 p1-c1");
  }

  @Test
  void aRejectedShortUnwindsTheWing() {
    broker.reject.add("SELL N24500PE");
    var out = service.open("p1", SPREAD);
    assertThat(out.ok()).isFalse();
    assertThat(out.rejection()).hasValueSatisfying(m -> assertThat(m).contains("test rejection"));
    assertThat(broker.sent).containsExactly("BUY N24200PE 130 p1-o0", "SELL N24200PE 130 p1-u0");
    assertThat(out.unwind()).hasSize(1);
    assertThat(out.charges()).isEqualTo(2);
  }

  @Test
  void anUnwindThatIsRejectedLeavesTheLegForTheCaller() {
    broker.reject.add("SELL N24500PE");
    broker.reject.add("SELL N24200PE");
    var out = service.open("p1", SPREAD);
    assertThat(out.fills()).hasSize(1);
    assertThat(out.unwind()).isEmpty();
  }

  @Test
  void aRejectedBuyBackStopsTheCloseSoTheWingStays() {
    broker.reject.add("BUY N24500PE");
    var out = service.close("p1", SPREAD);
    assertThat(out.ok()).isFalse();
    assertThat(broker.sent).isEmpty(); // the long wing is not sold while the short is still open
  }
}
