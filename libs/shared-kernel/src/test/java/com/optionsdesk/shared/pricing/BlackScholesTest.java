package com.optionsdesk.shared.pricing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.within;

import com.optionsdesk.shared.domain.Right;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.DoubleRange;
import net.jqwik.api.constraints.Scale;
import org.junit.jupiter.api.Test;

class BlackScholesTest {

  // Textbook case (Hull): S = K = 100, T = 1 year, r = 5%, σ = 20% → call 10.4506, put 5.5735
  @Test
  void matchesTheTextbookValues() {
    assertThat(BlackScholes.price(100, 100, 1, 0.05, 0.20, Right.CE))
        .isCloseTo(10.4506, within(1e-3));
    assertThat(BlackScholes.price(100, 100, 1, 0.05, 0.20, Right.PE))
        .isCloseTo(5.5735, within(1e-3));
    assertThat(BlackScholes.delta(100, 100, 1, 0.05, 0.20, Right.CE))
        .isCloseTo(0.6368, within(1e-3));
  }

  @Test
  void rejectsImpossibleInputs() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> BlackScholes.price(-1, 100, 1, 0.05, 0.2, Right.CE));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> BlackScholes.delta(100, 0, 1, 0.05, 0.2, Right.PE));
  }

  /**
   * At expiry (or with zero volatility) an option is worth its intrinsic value and has no time
   * Greeks.
   */
  @Test
  void atExpiryEverythingIsIntrinsic() {
    assertThat(BlackScholes.price(24_800, 24_650, 0, 0.065, 0.2, Right.CE)).isEqualTo(150);
    assertThat(BlackScholes.price(24_800, 24_650, 0.1, 0.065, 0, Right.PE)).isZero();
    assertThat(BlackScholes.delta(24_800, 24_650, 0, 0.065, 0.2, Right.CE)).isEqualTo(1);
    assertThat(BlackScholes.delta(24_800, 24_650, 0, 0.065, 0.2, Right.PE)).isZero();
    assertThat(BlackScholes.delta(24_500, 24_650, 0, 0.065, 0.2, Right.PE)).isEqualTo(-1);
    assertThat(BlackScholes.gamma(24_800, 24_650, 0, 0.065, 0.2)).isZero();
    assertThat(BlackScholes.vega(24_800, 24_650, 0, 0.065, 0.2)).isZero();
    assertThat(BlackScholes.theta(24_800, 24_650, 0, 0.065, 0.2, Right.CE)).isZero();
  }

  @Test
  void impliedVolRoundTripsAndRefusesImpossiblePrices() {
    double price = BlackScholes.price(24_650, 24_650, 7.0 / 365, 0.065, 0.14, Right.CE);
    assertThat(BlackScholes.impliedVol(price, 24_650, 24_650, 7.0 / 365, 0.065, Right.CE))
        .hasValueCloseTo(0.14, within(1e-5));
    assertThat(BlackScholes.impliedVol(0, 24_650, 24_650, 0.1, 0.065, Right.CE)).isEmpty();
    assertThat(BlackScholes.impliedVol(100, 24_900, 24_650, 0.1, 0.065, Right.CE))
        .isEmpty(); // below intrinsic
    assertThat(BlackScholes.impliedVol(30_000, 24_650, 24_650, 0.1, 0.065, Right.CE))
        .isEmpty(); // above any vol
  }

  @Test
  void yearsToExpiryCountsToTheSettlementTime() {
    var expiry = java.time.LocalDate.of(2026, 11, 10);
    var now =
        java.time.LocalDateTime.parse("2026-11-09T15:30")
            .atZone(com.optionsdesk.shared.calendar.MarketCalendar.IST);
    assertThat(BlackScholes.yearsToExpiry(expiry, now, java.time.LocalTime.of(15, 30)))
        .isCloseTo(1.0 / 365, within(1e-12));
    assertThat(BlackScholes.yearsToExpiry(expiry, now.plusDays(2), java.time.LocalTime.of(15, 30)))
        .isEqualTo(BlackScholes.MIN_YEARS);
  }

  /** Put–call parity holds for any inputs: C − P = S − K·e^(−rT). */
  @Property
  void putCallParity(
      @ForAll @DoubleRange(min = 10_000, max = 90_000) double spot,
      @ForAll @DoubleRange(min = 0.7, max = 1.3) double moneyness,
      @ForAll @DoubleRange(min = 0.001, max = 1.0) @Scale(4) double years,
      @ForAll @DoubleRange(min = 0.0, max = 0.10) double rate,
      @ForAll @DoubleRange(min = 0.05, max = 0.80) double vol) {
    double strike = spot * moneyness;
    double call = BlackScholes.price(spot, strike, years, rate, vol, Right.CE);
    double put = BlackScholes.price(spot, strike, years, rate, vol, Right.PE);
    assertThat(call - put).isCloseTo(spot - strike * Math.exp(-rate * years), within(spot * 1e-6));
  }

  /** Prices stay within their no-arbitrage bounds; deltas stay in range. */
  @Property
  void boundsHold(
      @ForAll @DoubleRange(min = 10_000, max = 90_000) double spot,
      @ForAll @DoubleRange(min = 0.7, max = 1.3) double moneyness,
      @ForAll @DoubleRange(min = 0.001, max = 1.0) @Scale(4) double years,
      @ForAll @DoubleRange(min = 0.05, max = 0.80) double vol) {
    double strike = spot * moneyness;
    double call = BlackScholes.price(spot, strike, years, 0.065, vol, Right.CE);
    double put = BlackScholes.price(spot, strike, years, 0.065, vol, Right.PE);
    double tolerance = spot * 1e-6;
    assertThat(call).isBetween(-tolerance, spot + tolerance);
    assertThat(put).isBetween(-tolerance, strike + tolerance);
    assertThat(BlackScholes.delta(spot, strike, years, 0.065, vol, Right.CE)).isBetween(0.0, 1.0);
    assertThat(BlackScholes.delta(spot, strike, years, 0.065, vol, Right.PE)).isBetween(-1.0, 0.0);
  }
}
