package com.optionsdesk.shared.indicators;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Wilder's ADX(n) with the directional indicators +DI and −DI. Ported from the legacy research code
 * and checked against it.
 */
public final class Adx {

  /** ADX, +DI and −DI after a bar (0–100 each). */
  public record Reading(double adx, double plusDi, double minusDi) {}

  private Adx() {}

  /**
   * The reading after each bar, aligned with {@code bars}; empty until there is enough history. ±DI
   * first exist at index {@code n} (n true ranges after the first bar), and the ADX averages the
   * first {@code n} DX values, so the first reading is at index {@code 2n − 1}.
   */
  public static List<Optional<Reading>> compute(List<Bar> bars, int n) {
    if (n < 1) {
      throw new IllegalArgumentException("n must be at least 1");
    }
    List<Optional<Reading>> out = new ArrayList<>(bars.size());
    double trS = 0;
    double plusS = 0;
    double minusS = 0;
    int warm = 0;
    double adx = 0;
    double dxSum = 0;
    int dxCount = 0;
    boolean adxReady = false;
    for (int i = 0; i < bars.size(); i++) {
      if (i == 0) {
        out.add(Optional.empty());
        continue;
      }
      Bar prev = bars.get(i - 1);
      Bar b = bars.get(i);
      double tr = b.trueRange(prev.close());
      double up = b.high() - prev.high();
      double down = prev.low() - b.low();
      double plusDm = up > down && up > 0 ? up : 0.0;
      double minusDm = down > up && down > 0 ? down : 0.0;
      if (warm < n) {
        trS += tr;
        plusS += plusDm;
        minusS += minusDm;
        warm++;
        if (warm < n) {
          out.add(Optional.empty());
          continue;
        }
      } else {
        trS = trS - trS / n + tr;
        plusS = plusS - plusS / n + plusDm;
        minusS = minusS - minusS / n + minusDm;
      }
      double plusDi = 100 * plusS / trS;
      double minusDi = 100 * minusS / trS;
      double dx =
          plusDi + minusDi != 0 ? 100 * Math.abs(plusDi - minusDi) / (plusDi + minusDi) : 0.0;
      if (!adxReady) {
        dxSum += dx;
        dxCount++;
        if (dxCount < n) {
          out.add(Optional.empty());
          continue;
        }
        adx = dxSum / n;
        adxReady = true;
      } else {
        adx = (adx * (n - 1) + dx) / n;
      }
      out.add(Optional.of(new Reading(adx, plusDi, minusDi)));
    }
    return out;
  }
}
