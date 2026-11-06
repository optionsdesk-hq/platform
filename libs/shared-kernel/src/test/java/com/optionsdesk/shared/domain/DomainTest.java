package com.optionsdesk.shared.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class DomainTest {

  static final LocalDate EXPIRY = LocalDate.of(2026, 11, 17);
  static final Instant T0 = Instant.parse("2026-11-06T04:20:00Z");

  static OptionContract contract(double strike, Right right) {
    return new OptionContract("NIFTY" + (int) strike + right, "NIFTY", EXPIRY, strike, right, 65);
  }

  @Test
  void intrinsicValues() {
    assertThat(Right.CE.intrinsic(24_800, 24_650)).isEqualTo(150);
    assertThat(Right.CE.intrinsic(24_500, 24_650)).isZero();
    assertThat(Right.PE.intrinsic(24_500, 24_650)).isEqualTo(150);
    assertThat(Right.PE.intrinsic(24_800, 24_650)).isZero();
  }

  @Test
  void contractsAreValidated() {
    assertThatIllegalArgumentException().isThrownBy(() -> contract(0, Right.CE));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new OptionContract("X", "NIFTY", EXPIRY, 100, Right.CE, 0));
    assertThatNullPointerException()
        .isThrownBy(() -> new OptionContract(null, "NIFTY", EXPIRY, 100, Right.CE, 65));
  }

  @Test
  void quoteMidSpreadAndStaleness() {
    OptionContract c = contract(24_650, Right.CE);
    Quote both = new Quote(c, 150.0, 149.5, 150.5, T0);
    assertThat(both.mid()).hasValue(150.0);
    assertThat(both.spread()).hasValue(1.0);
    Quote noAsk = new Quote(c, 151.0, 149.5, 0, T0);
    assertThat(noAsk.mid()).hasValue(151.0); // falls back to the last trade
    assertThat(noAsk.spread()).isEmpty();
    assertThat(new Quote(c, 0, 0, 0, T0).mid()).isEmpty();
    assertThat(both.isStale(T0.plusSeconds(10), Duration.ofSeconds(5))).isTrue();
    assertThat(both.isStale(T0.plusSeconds(3), Duration.ofSeconds(5))).isFalse();
    assertThatIllegalArgumentException().isThrownBy(() -> new Quote(c, -1, 0, 0, T0));
  }

  /** A short PE leg: sold at 190.00, bought back at 65.80, 1 lot of 65 → +₹8,073. */
  @Test
  void shortLegProfitAndExpiryPayoff() {
    Leg pe = new Leg(contract(24_650, Right.PE), -1, 190.00);
    assertThat(pe.isShort()).isTrue();
    assertThat(pe.quantity()).isEqualTo(-65);
    assertThat(pe.markToMarket(65.80))
        .isCloseTo(8_073.0, org.assertj.core.data.Offset.offset(0.01));
    assertThat(pe.payoffAtExpiry(24_881))
        .isCloseTo(12_350.0, org.assertj.core.data.Offset.offset(0.01));
    assertThatIllegalArgumentException().isThrownBy(() -> new Leg(contract(1, Right.CE), 0, 1));
    assertThatIllegalArgumentException().isThrownBy(() -> new Leg(contract(1, Right.CE), 1, -1));
  }
}
