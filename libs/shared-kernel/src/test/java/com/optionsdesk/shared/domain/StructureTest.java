package com.optionsdesk.shared.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.within;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StructureTest {

  static final LocalDate EXPIRY = LocalDate.of(2026, 11, 17);

  static Leg leg(String symbol, double strike, Right right, int lots, double entry) {
    return new Leg(new OptionContract(symbol, "NIFTY", EXPIRY, strike, right, 65), lots, entry);
  }

  /**
   * A bull put spread, 2 lots: short 24500 PE at 80, long 24200 PE at 20 → credit 60 pts, width
   * 300.
   */
  final Structure bullPut =
      new Structure(
          List.of(leg("P24500", 24_500, Right.PE, -2, 80), leg("P24200", 24_200, Right.PE, 2, 20)),
          true);

  @Test
  void markAndPayoff() {
    assertThat(bullPut.markPnl(Map.of("P24500", 50.0, "P24200", 10.0)))
        .isCloseTo((80 - 50) * 130 - (20 - 10) * 130, within(1e-9));
    assertThat(bullPut.payoffAt(25_000)).isCloseTo(60 * 130, within(1e-9)); // both expire worthless
    assertThat(bullPut.payoffAt(24_000))
        .isCloseTo((60 - 300) * 130, within(1e-9)); // full width lost
  }

  @Test
  void payoffRangeIsMaxProfitAndMaxLoss() {
    double[] range = bullPut.payoffRange();
    assertThat(range[1]).isCloseTo(60 * 130, within(1e-9));
    assertThat(range[0]).isCloseTo(-240 * 130, within(1e-9));
  }

  @Test
  void expiriesAndValidation() {
    assertThat(bullPut.singleExpiry()).isTrue();
    var far =
        new Leg(
            new OptionContract("F", "NIFTY", EXPIRY.plusDays(28), 24_500, Right.PE, 65), 1, 150);
    assertThat(new Structure(List.of(bullPut.legs().get(0), far), true).singleExpiry()).isFalse();
    assertThatIllegalArgumentException().isThrownBy(() -> new Structure(List.of(), true));
    assertThatIllegalArgumentException().isThrownBy(() -> bullPut.markPnl(Map.of("P24500", 50.0)));
  }
}
