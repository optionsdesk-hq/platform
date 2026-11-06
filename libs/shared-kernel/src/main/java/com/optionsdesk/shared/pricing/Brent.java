package com.optionsdesk.shared.pricing;

import java.util.OptionalDouble;
import java.util.function.DoubleUnaryOperator;

/**
 * Brent's root-finding method: bisection's guarantee with the speed of secant and inverse quadratic
 * interpolation. The same algorithm as scipy's {@code brentq} (and Numerical Recipes' {@code
 * zbrent}), so implied volatilities match the legacy Python system.
 */
final class Brent {

  private static final double EPS = Math.ulp(1.0);

  private Brent() {}

  /**
   * A root of {@code f} in [{@code low}, {@code high}], or empty when f has the same sign at both
   * ends (no root bracketed) or the iterations run out.
   */
  static OptionalDouble root(
      DoubleUnaryOperator f, double low, double high, double tol, int maxIter) {
    double a = low;
    double b = high;
    double fa = f.applyAsDouble(a);
    double fb = f.applyAsDouble(b);
    if ((fa > 0 && fb > 0) || (fa < 0 && fb < 0)) {
      return OptionalDouble.empty();
    }
    double c = b;
    double fc = fb;
    double d = 0;
    double e = 0;
    for (int i = 0; i < maxIter; i++) {
      if ((fb > 0 && fc > 0) || (fb < 0 && fc < 0)) {
        c = a; // keep the root bracketed between b and c
        fc = fa;
        d = b - a;
        e = d;
      }
      if (Math.abs(fc) < Math.abs(fb)) { // b is always the best estimate
        a = b;
        b = c;
        c = a;
        fa = fb;
        fb = fc;
        fc = fa;
      }
      double tol1 = 2 * EPS * Math.abs(b) + 0.5 * tol;
      double xm = 0.5 * (c - b);
      if (Math.abs(xm) <= tol1 || fb == 0) {
        return OptionalDouble.of(b);
      }
      if (Math.abs(e) >= tol1 && Math.abs(fa) > Math.abs(fb)) {
        double s = fb / fa;
        double p;
        double q;
        if (a == c) { // secant
          p = 2 * xm * s;
          q = 1 - s;
        } else { // inverse quadratic interpolation
          double qq = fa / fc;
          double r = fb / fc;
          p = s * (2 * xm * qq * (qq - r) - (b - a) * (r - 1));
          q = (qq - 1) * (r - 1) * (s - 1);
        }
        if (p > 0) {
          q = -q;
        }
        p = Math.abs(p);
        double min1 = 3 * xm * q - Math.abs(tol1 * q);
        double min2 = Math.abs(e * q);
        if (2 * p < Math.min(min1, min2)) {
          e = d; // interpolation accepted
          d = p / q;
        } else {
          d = xm; // fall back to bisection
          e = d;
        }
      } else {
        d = xm;
        e = d;
      }
      a = b;
      fa = fb;
      b += Math.abs(d) > tol1 ? d : Math.copySign(tol1, xm);
      fb = f.applyAsDouble(b);
    }
    return OptionalDouble.empty();
  }
}
