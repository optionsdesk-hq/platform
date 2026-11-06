package com.optionsdesk.shared.money;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

class ChargesTest {

  // Zerodha F&O options rates in the rulebook (Nov 2026)
  static final Charges ZERODHA = new Charges(20.0, 0.1, 0.03503, 10.0, 0.003, 18.0, 2, 0.05);

  /** Selling 1 lot (65) of the 24650 PE at ₹190: turnover ₹12,350. */
  @Test
  void sellOrderIncludesSttButNoStamp() {
    double turnover = 190.0 * 65;
    double exchange = turnover * 0.03503 / 100;
    double sebi = turnover * 10 / 1e7;
    double expected = 20 + exchange + sebi + turnover * 0.1 / 100 + (20 + exchange + sebi) * 0.18;
    assertThat(ZERODHA.order(turnover, true)).isCloseTo(expected, within(1e-9));
  }

  @Test
  void buyOrderIncludesStampButNoStt() {
    double turnover = 65.8 * 65;
    assertThat(ZERODHA.order(turnover, false))
        .isLessThan(ZERODHA.order(turnover, true))
        .isGreaterThan(20); // brokerage alone
  }

  @Test
  void slippageMovesAgainstUsAndNeverBelowOneTick() {
    assertThat(ZERODHA.slippedPrice(150.00, true)).isEqualTo(150.10);
    assertThat(ZERODHA.slippedPrice(150.00, false)).isEqualTo(149.90);
    assertThat(ZERODHA.slippedPrice(0.05, false)).isEqualTo(0.05);
  }

  @Test
  void ratesAreValidated() {
    assertThatIllegalArgumentException().isThrownBy(() -> new Charges(-1, 0, 0, 0, 0, 0, 0, 0.05));
    assertThatIllegalArgumentException().isThrownBy(() -> new Charges(20, 0, 0, 0, 0, 0, 0, 0));
  }
}
