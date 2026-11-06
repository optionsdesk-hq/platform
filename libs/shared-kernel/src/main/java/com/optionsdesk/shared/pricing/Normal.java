package com.optionsdesk.shared.pricing;

/** Standard normal distribution helpers. */
final class Normal {

  private Normal() {}

  /**
   * Cumulative distribution function, via the complementary error function (Numerical Recipes
   * {@code erfc} Chebyshev fit, absolute error below 1.2e-7 everywhere): far more than option
   * pricing needs.
   */
  static double cdf(double x) {
    return 0.5 * erfc(-x / Math.sqrt(2.0));
  }

  /** Probability density function. */
  static double pdf(double x) {
    return Math.exp(-0.5 * x * x) / Math.sqrt(2 * Math.PI);
  }

  private static double erfc(double x) {
    double z = Math.abs(x);
    double t = 1.0 / (1.0 + 0.5 * z);
    double r =
        t
            * Math.exp(
                -z * z
                    - 1.26551223
                    + t
                        * (1.00002368
                            + t
                                * (0.37409196
                                    + t
                                        * (0.09678418
                                            + t
                                                * (-0.18628806
                                                    + t
                                                        * (0.27886807
                                                            + t
                                                                * (-1.13520398
                                                                    + t
                                                                        * (1.48851587
                                                                            + t
                                                                                * (-0.82215223
                                                                                    + t
                                                                                        * 0.17087277)))))))));
    return x >= 0 ? r : 2.0 - r;
  }
}
