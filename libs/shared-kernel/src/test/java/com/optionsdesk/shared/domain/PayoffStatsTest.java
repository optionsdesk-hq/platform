package com.optionsdesk.shared.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class PayoffStatsTest {

  static final LocalDate EXPIRY = LocalDate.of(2026, 11, 17);

  static Leg leg(double strike, Right right, int lots, double entry) {
    return new Leg(
        new OptionContract("S" + (int) strike + right, "NIFTY", EXPIRY, strike, right, 65),
        lots,
        entry);
  }

  /**
   * Bull put 24500/24200, 1 lot, credit 60 pts: breakeven 24440, max profit 3,900, max loss
   * −15,600.
   */
  @Test
  void bullPutSpread() {
    var s =
        new Structure(List.of(leg(24_500, Right.PE, -1, 80), leg(24_200, Right.PE, 1, 20)), true);
    var stats = PayoffStats.of(s, 24_600);
    assertThat(stats.credit()).isCloseTo(60 * 65, within(1e-9));
    assertThat(stats.maxProfit()).isCloseTo(60 * 65, within(1e-6));
    assertThat(stats.maxLoss()).isCloseTo(-240 * 65, within(1e-6));
    assertThat(stats.breakevens())
        .singleElement()
        .satisfies(b -> assertThat(b).isCloseTo(24_440, within(0.1)));
    assertThat(stats.creditToWidth()).hasValueCloseTo(0.2, within(1e-12)); // 60 / 300
    assertThat(s.spreadWidth()).hasValue(300);
  }

  @Test
  void nakedStructuresHaveNoWidthOrCreditToWidth() {
    var strangle =
        new Structure(List.of(leg(24_200, Right.PE, -1, 60), leg(25_000, Right.CE, -1, 55)), false);
    assertThat(strangle.spreadWidth()).isEmpty();
    var stats = PayoffStats.of(strangle, 24_600);
    assertThat(stats.creditToWidth()).isEmpty();
    assertThat(stats.breakevens()).hasSize(2);
  }
}
