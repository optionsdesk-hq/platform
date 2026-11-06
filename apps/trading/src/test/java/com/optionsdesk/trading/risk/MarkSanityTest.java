package com.optionsdesk.trading.risk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.within;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.optionsdesk.shared.domain.Leg;
import com.optionsdesk.shared.domain.OptionContract;
import com.optionsdesk.shared.domain.Right;
import com.optionsdesk.shared.domain.Structure;
import com.optionsdesk.trading.risk.MarkSanity.Kind;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

class MarkSanityTest {

  static final LocalDate EXPIRY = LocalDate.of(2026, 11, 17);

  static Leg leg(String symbol, double strike, Right right, int lots, double entry) {
    return new Leg(new OptionContract(symbol, "NIFTY", EXPIRY, strike, right, 65), lots, entry);
  }

  /** 24000/23700 put spread + 25000/25300 call spread, credit (40−15)+(35−12) = 48 points. */
  static Structure condor() {
    return new Structure(
        List.of(
            leg("P24000", 24_000, Right.PE, -1, 40),
            leg("P23700", 23_700, Right.PE, 1, 15),
            leg("C25000", 25_000, Right.CE, -1, 35),
            leg("C25300", 25_300, Right.CE, 1, 12)),
        true);
  }

  @Test
  void payoffRangeOfACondor() {
    double[] range = condor().payoffRange();
    assertThat(range[1]).isCloseTo(48 * 65, within(1e-9));
    assertThat(range[0]).isCloseTo((48 - 300) * 65, within(1e-9));
  }

  @Test
  void sanePricesPass() {
    assertThat(
            MarkSanity.check(
                condor(), Map.of("P24000", 30.0, "P23700", 10.0, "C25000", 50.0, "C25300", 20.0)))
        .isEmpty();
  }

  @Test
  void aMissingPriceIsRejected() {
    var problem =
        MarkSanity.check(
            condor(), Map.of("P24000", 30.0, "P23700", 0.0, "C25000", 50.0, "C25300", 20.0));
    assertThat(problem).hasValueSatisfying(p -> assertThat(p.kind()).isEqualTo(Kind.MISSING_PRICE));
    assertThat(problem.orElseThrow().message()).contains("P23700");
  }

  /**
   * The PRE-38 shape: a bad print on a long wing makes the condor "earn" more than its whole
   * credit.
   */
  @Test
  void anImpossibleGainIsRejected() {
    assertThat(
            MarkSanity.check(
                condor(), Map.of("P24000", 1.0, "P23700", 300.0, "C25000", 1.0, "C25300", 1.0)))
        .hasValueSatisfying(p -> assertThat(p.kind()).isEqualTo(Kind.ABOVE_MAX_PROFIT));
  }

  @Test
  void anImpossibleLossIsRejectedForDefinedRiskOnly() {
    var prices = Map.of("P24000", 500.0, "P23700", 1.0, "C25000", 1.0, "C25300", 1.0);
    assertThat(MarkSanity.check(condor(), prices))
        .hasValueSatisfying(p -> assertThat(p.kind()).isEqualTo(Kind.BELOW_MAX_LOSS));
    var naked =
        new Structure(
            List.of(
                leg("P24000", 24_000, Right.PE, -1, 40), leg("C25000", 25_000, Right.CE, -1, 35)),
            false);
    assertThat(MarkSanity.check(naked, Map.of("P24000", 500.0, "C25000", 1.0))).isEmpty();
  }

  @Test
  void calendarsSkipTheExpiryBound() {
    var far = new OptionContract("C25000F", "NIFTY", EXPIRY.plusDays(28), 25_000, Right.CE, 65);
    var calendar =
        new Structure(List.of(leg("C25000", 25_000, Right.CE, -1, 35), new Leg(far, 1, 120)), true);
    assertThat(MarkSanity.check(calendar, Map.of("C25000", 900.0, "C25000F", 1.0))).isEmpty();
  }

  @Test
  void structuresAreValidated() {
    assertThatIllegalArgumentException().isThrownBy(() -> new Structure(List.of(), true));
    assertThatIllegalArgumentException().isThrownBy(() -> condor().markPnl(Map.of()));
  }

  /**
   * 300 random condors, spreads, strangles and straddles judged by the legacy Python: Java must
   * agree on each.
   */
  @TestFactory
  Stream<DynamicTest> agreesWithThePythonVerdicts() throws IOException {
    JsonNode golden;
    try (InputStream in = getClass().getResourceAsStream("/golden/mark-sanity.json")) {
      golden = new ObjectMapper().readTree(in);
    }
    JsonNode cases = golden.get("cases");
    return Stream.iterate(0, i -> i < cases.size(), i -> i + 1)
        .map(
            i ->
                DynamicTest.dynamicTest(
                    "case " + i + " (" + cases.get(i).get("kind").asText() + ")",
                    () -> {
                      JsonNode c = cases.get(i);
                      List<Leg> legs = new ArrayList<>();
                      for (JsonNode l : c.get("legs")) {
                        var contract =
                            new OptionContract(
                                l.get("symbol").asText(),
                                "NIFTY",
                                EXPIRY,
                                l.get("strike").asDouble(),
                                Right.valueOf(l.get("right").asText()),
                                l.get("lot_size").asInt());
                        legs.add(
                            new Leg(contract, l.get("lots").asInt(), l.get("entry").asDouble()));
                      }
                      var structure = new Structure(legs, c.get("defined_risk").asBoolean());
                      Map<String, Double> prices = new HashMap<>();
                      c.get("prices")
                          .fields()
                          .forEachRemaining(e -> prices.put(e.getKey(), e.getValue().asDouble()));
                      double[] range = structure.payoffRange();
                      assertThat(range[0]).isCloseTo(c.get("payoff_low").asDouble(), within(1e-6));
                      assertThat(range[1]).isCloseTo(c.get("payoff_high").asDouble(), within(1e-6));
                      String expected =
                          c.get("problem").isNull() ? null : c.get("problem").asText();
                      String actual =
                          MarkSanity.check(structure, prices)
                              .map(
                                  p ->
                                      switch (p.kind()) {
                                        case MISSING_PRICE -> "missing";
                                        case ABOVE_MAX_PROFIT -> "above";
                                        case BELOW_MAX_LOSS -> "below";
                                      })
                              .orElse(null);
                      assertThat(actual).isEqualTo(expected);
                    }));
  }
}
