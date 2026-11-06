package com.optionsdesk.shared.indicators;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Behaviour on simple series; exact values are pinned by the replay golden tests in apps/trading.
 */
class IndicatorsTest {

  static List<Bar> line(double start, double step, int count) {
    List<Bar> bars = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      double c = start + step * i;
      bars.add(new Bar(c + 2, c - 2, c));
    }
    return bars;
  }

  @Test
  void supertrendStaysUpInARiseAndFlipsDownInACrash() {
    List<Bar> bars = new ArrayList<>(line(100, 1, 30));
    bars.addAll(line(128, -10, 10));
    int[] d = Supertrend.directions(bars, 10, 3);
    assertThat(d[29]).isEqualTo(Supertrend.UP);
    assertThat(d[d.length - 1]).isEqualTo(Supertrend.DOWN);
  }

  @Test
  void supertrendFlipsBackUpAfterARecovery() {
    List<Bar> bars = new ArrayList<>(line(200, -1, 30));
    bars.addAll(line(171, 10, 10));
    int[] d = Supertrend.directions(bars, 7, 2);
    assertThat(d[29]).isEqualTo(Supertrend.DOWN);
    assertThat(d[d.length - 1]).isEqualTo(Supertrend.UP);
  }

  @Test
  void adxNeedsTwoPeriodsOfHistoryThenReadsAStrongTrend() {
    var r = Adx.compute(line(100, 3, 40), 14);
    assertThat(r.subList(0, 27)).allMatch(Optional::isEmpty); // first reading at index 2n − 1
    assertThat(r.get(27)).isPresent();
    Adx.Reading last = r.get(39).orElseThrow();
    assertThat(last.plusDi()).isGreaterThan(last.minusDi());
    assertThat(last.minusDi()).isCloseTo(0, within(1e-9));
    assertThat(last.adx()).isCloseTo(100, within(1e-9));
  }

  @Test
  void flatBarsGiveZeroDirectionalMovement() {
    List<Bar> flat = new ArrayList<>();
    for (int i = 0; i < 40; i++) {
      flat.add(new Bar(101, 99, 100));
    }
    Adx.Reading last = Adx.compute(flat, 14).get(39).orElseThrow();
    assertThat(last.adx()).isZero();
    assertThat(last.plusDi()).isZero();
  }

  @Test
  void rejectsAnEmptyPeriod() {
    assertThatThrownBy(() -> Adx.compute(List.of(), 0))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> Supertrend.directions(List.of(), 0, 3))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
