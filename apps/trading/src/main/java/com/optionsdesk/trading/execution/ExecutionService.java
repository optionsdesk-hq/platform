package com.optionsdesk.trading.execution;

import com.optionsdesk.shared.domain.Leg;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Opens and closes multi-leg positions in a safe order, so the position is never naked mid-trade:
 *
 * <ul>
 *   <li><b>open:</b> buy the long legs (wings) first, then sell the shorts;
 *   <li><b>close:</b> buy back the shorts first, then sell the longs.
 * </ul>
 *
 * If an opening leg is rejected, every leg already filled is closed again in the safe order (an
 * unwind) and the open fails. A rejected closing leg stops the close: the remaining legs stay open
 * for the caller to retry (closing the long wing first could leave a naked short).
 */
public final class ExecutionService {

  /** What happened: every fill (including any unwind), and the rejection if there was one. */
  public record Outcome(List<Execution> fills, List<Execution> unwind, Optional<String> rejection) {

    public boolean ok() {
      return rejection.isEmpty();
    }

    /** ₹ cash in (+) or out (−) over the fills (not the unwind), before charges. */
    public double cashFlow() {
      return fills.stream().mapToDouble(Execution::cashFlow).sum();
    }

    /** ₹ charges over every fill, the unwind included. */
    public double charges() {
      return fills.stream().mapToDouble(Execution::charges).sum()
          + unwind.stream().mapToDouble(Execution::charges).sum();
    }
  }

  private final Broker broker;

  public ExecutionService(Broker broker) {
    this.broker = broker;
  }

  /**
   * Open {@code legs} (signed lots; entry prices are ignored). {@code tag} prefixes each order's
   * idempotency tag.
   */
  public Outcome open(String tag, List<Leg> legs) {
    List<Leg> ordered = new ArrayList<>(legs);
    ordered.sort(Comparator.comparing(Leg::isShort)); // longs (false) first
    List<Execution> fills = new ArrayList<>();
    for (int i = 0; i < ordered.size(); i++) {
      Leg leg = ordered.get(i);
      var side = leg.isShort() ? OrderRequest.Side.SELL : OrderRequest.Side.BUY;
      try {
        fills.add(
            broker.execute(
                new OrderRequest(leg.contract(), side, Math.abs(leg.quantity()), tag + "-o" + i)));
      } catch (Broker.OrderRejected e) {
        return new Outcome(fills, unwind(tag, fills), Optional.of(e.getMessage()));
      }
    }
    return new Outcome(fills, List.of(), Optional.empty());
  }

  /** Close {@code legs} (the open position's legs, signed lots). */
  public Outcome close(String tag, List<Leg> legs) {
    List<Leg> ordered = new ArrayList<>(legs);
    ordered.sort(Comparator.comparing(l -> !l.isShort())); // shorts first
    List<Execution> fills = new ArrayList<>();
    for (int i = 0; i < ordered.size(); i++) {
      Leg leg = ordered.get(i);
      var side = leg.isShort() ? OrderRequest.Side.BUY : OrderRequest.Side.SELL;
      try {
        fills.add(
            broker.execute(
                new OrderRequest(leg.contract(), side, Math.abs(leg.quantity()), tag + "-c" + i)));
      } catch (Broker.OrderRejected e) {
        return new Outcome(fills, List.of(), Optional.of(e.getMessage()));
      }
    }
    return new Outcome(fills, List.of(), Optional.empty());
  }

  private List<Execution> unwind(String tag, List<Execution> filled) {
    List<Execution> ordered = new ArrayList<>(filled);
    ordered.sort(Comparator.comparing(e -> !e.request().isSell())); // buy back the sold first
    List<Execution> out = new ArrayList<>();
    for (int i = 0; i < ordered.size(); i++) {
      var r = ordered.get(i).request();
      var back = r.isSell() ? OrderRequest.Side.BUY : OrderRequest.Side.SELL;
      try {
        out.add(broker.execute(new OrderRequest(r.contract(), back, r.quantity(), tag + "-u" + i)));
      } catch (Broker.OrderRejected e) {
        // left open: the caller sees fills without matching unwind fills and must alert (position
        // FAILED)
        break;
      }
    }
    return out;
  }
}
