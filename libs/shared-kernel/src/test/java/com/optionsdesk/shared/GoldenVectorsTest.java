package com.optionsdesk.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.optionsdesk.shared.domain.Right;
import com.optionsdesk.shared.money.Charges;
import com.optionsdesk.shared.pricing.BlackScholes;
import java.io.IOException;
import java.io.InputStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * Golden tests: 403 pricing cases and 100 charge cases computed by the legacy Python system
 * (seeded, generated once). The Java port must reproduce every one, so the migration provably
 * computes the same numbers.
 *
 * <p>Tolerances: Java's normal CDF approximation is accurate to ~1e-7, so prices are compared
 * within 1e-6 of the index level; densities (gamma, vega) are exact and compared within a relative
 * 1e-6.
 */
class GoldenVectorsTest {

  static final JsonNode GOLDEN = load("/golden/pricing-and-charges.json");

  @TestFactory
  Stream<DynamicTest> pricingAndGreeksMatchPython() {
    return Stream.iterate(0, i -> i < GOLDEN.get("pricing").size(), i -> i + 1)
        .map(
            i -> {
              JsonNode c = GOLDEN.get("pricing").get(i);
              return DynamicTest.dynamicTest("pricing case " + i, () -> checkPricing(c));
            });
  }

  static void checkPricing(JsonNode c) {
    double s = c.get("s").asDouble();
    double k = c.get("k").asDouble();
    double t = c.get("t").asDouble();
    double r = c.get("r").asDouble();
    double v = c.get("sigma").asDouble();
    Right right = Right.valueOf(c.get("right").asText());
    double priceTol = 1e-6 * s;

    double price = BlackScholes.price(s, k, t, r, v, right);
    assertThat(price).isCloseTo(c.get("price").asDouble(), within(priceTol));
    assertThat(BlackScholes.delta(s, k, t, r, v, right))
        .isCloseTo(c.get("delta").asDouble(), within(1e-6));
    assertThat(BlackScholes.gamma(s, k, t, r, v))
        .isCloseTo(c.get("gamma").asDouble(), relative(c, "gamma"));
    assertThat(BlackScholes.vega(s, k, t, r, v))
        .isCloseTo(c.get("vega").asDouble(), relative(c, "vega"));
    assertThat(BlackScholes.theta(s, k, t, r, v, right))
        .isCloseTo(
            c.get("theta").asDouble(),
            within(1e-6 * Math.max(1, Math.abs(c.get("theta").asDouble())) + 1e-6 * s * r / 365));

    var iv = BlackScholes.impliedVol(c.get("price").asDouble(), s, k, t, r, right);
    if (c.get("iv").isNull()) {
      assertThat(iv).isEmpty();
    } else {
      // Both solvers stop within 1e-6; check what matters: the Java IV reprices the option.
      assertThat(iv).isPresent();
      assertThat(BlackScholes.price(s, k, t, r, iv.getAsDouble(), right))
          .isCloseTo(
              c.get("price").asDouble(),
              within(priceTol + 1e-6 * Math.max(1, c.get("price").asDouble())));
    }
  }

  @TestFactory
  Stream<DynamicTest> chargesMatchPython() {
    JsonNode rates = GOLDEN.get("rates");
    Charges charges =
        new Charges(
            rates.get("brokerage_per_order").asDouble(),
            rates.get("stt_sell_pct_of_premium").asDouble(),
            rates.get("exchange_txn_pct_of_premium").asDouble(),
            rates.get("sebi_per_crore").asDouble(),
            rates.get("stamp_buy_pct_of_premium").asDouble(),
            rates.get("gst_pct").asDouble(),
            rates.get("slippage_ticks_per_leg").asInt(),
            rates.get("tick_size").asDouble());
    return Stream.iterate(0, i -> i < GOLDEN.get("charges").size(), i -> i + 1)
        .map(
            i -> {
              JsonNode c = GOLDEN.get("charges").get(i);
              return DynamicTest.dynamicTest(
                  "charges case " + i,
                  () ->
                      assertThat(
                              charges.order(
                                  c.get("turnover").asDouble(), c.get("sell").asBoolean()))
                          .isCloseTo(c.get("charges").asDouble(), within(1e-9)));
            });
  }

  private static org.assertj.core.data.Offset<Double> relative(JsonNode c, String field) {
    return within(1e-6 * Math.max(1e-12, Math.abs(c.get(field).asDouble())) + 1e-15);
  }

  private static JsonNode load(String resource) {
    try (InputStream in = GoldenVectorsTest.class.getResourceAsStream(resource)) {
      return new ObjectMapper().readTree(in);
    } catch (IOException e) {
      throw new IllegalStateException("can't read " + resource, e);
    }
  }
}
