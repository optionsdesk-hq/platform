package com.optionsdesk.shared.indicators;

import java.util.List;

/**
 * Supertrend(n, multiplier): bands at the bar's mid ± multiplier × ATR (Wilder), ratcheted; the
 * direction flips when the close crosses the band on the other side. Ported from the legacy
 * research code and checked against it.
 */
public final class Supertrend {

  /** Up trend. */
  public static final int UP = 1;

  /** Down trend. */
  public static final int DOWN = -1;

  private Supertrend() {}

  /**
   * The direction after each bar ({@link #UP} or {@link #DOWN}), aligned with {@code bars}. It
   * starts {@link #UP} and stays there until the ATR has {@code n} bars.
   */
  public static int[] directions(List<Bar> bars, int n, double multiplier) {
    if (n < 1) {
      throw new IllegalArgumentException("n must be at least 1");
    }
    int[] out = new int[bars.size()];
    int direction = UP;
    double prevClose = 0;
    double atr = 0;
    double trSum = 0;
    int trCount = 0;
    boolean atrReady = false;
    double upper = 0;
    double lower = 0;
    boolean bandsReady = false;
    for (int i = 0; i < bars.size(); i++) {
      Bar b = bars.get(i);
      if (i == 0) {
        prevClose = b.close();
        out[i] = direction;
        continue;
      }
      double tr = b.trueRange(prevClose);
      if (!atrReady) {
        trSum += tr;
        trCount++;
        if (trCount == n) {
          atr = trSum / n;
          atrReady = true;
        }
      } else {
        atr = (atr * (n - 1) + tr) / n;
      }
      if (atrReady) {
        double ub = (b.high() + b.low()) / 2 + multiplier * atr;
        double lb = (b.high() + b.low()) / 2 - multiplier * atr;
        if (!bandsReady) {
          upper = ub;
          lower = lb;
          bandsReady = true;
        } else {
          upper = ub < upper || prevClose > upper ? ub : upper;
          lower = lb > lower || prevClose < lower ? lb : lower;
        }
        if (direction == UP && b.close() < lower) {
          direction = DOWN;
        } else if (direction == DOWN && b.close() > upper) {
          direction = UP;
        }
      }
      prevClose = b.close();
      out[i] = direction;
    }
    return out;
  }
}
