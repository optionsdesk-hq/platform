package com.optionsdesk.shared.pricing;

import com.optionsdesk.shared.domain.Right;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.OptionalDouble;

/**
 * Black–Scholes price, Greeks and implied volatility for European index options (NSE index options
 * are European). Pure functions: no state, no I/O. Ported from the legacy Python {@code
 * optionsdesk.pricing.black_scholes} and checked against its outputs (golden tests).
 *
 * <p>Units: {@code years} to expiry; {@code rate} and {@code vol} as decimals (0.065, 0.14). At or
 * past expiry ({@code years <= MIN_YEARS}) or with zero volatility, prices are intrinsic and Greeks
 * are 0 (delta ±1 or 0).
 */
public final class BlackScholes {

  /** Smallest time to expiry used (years): avoids division by zero at expiry. */
  public static final double MIN_YEARS = 1e-6;

  private static final double IV_LOW = 1e-4;
  private static final double IV_HIGH = 5.0;
  private static final double SECONDS_PER_YEAR = 365.0 * 86_400;

  private BlackScholes() {}

  /**
   * Years from {@code now} to the expiry date's settlement time ({@code expiryTime} IST), at least
   * MIN_YEARS.
   */
  public static double yearsToExpiry(LocalDate expiry, ZonedDateTime now, LocalTime expiryTime) {
    ZonedDateTime settles =
        expiry.atTime(expiryTime).atZone(com.optionsdesk.shared.calendar.MarketCalendar.IST);
    double years = Duration.between(now, settles).toMillis() / 1000.0 / SECONDS_PER_YEAR;
    return Math.max(years, MIN_YEARS);
  }

  /** Option price per unit. */
  public static double price(
      double spot, double strike, double years, double rate, double vol, Right right) {
    requirePositive(spot, strike);
    if (expired(vol, years)) {
      return right.intrinsic(spot, strike);
    }
    double d1 = d1(spot, strike, years, rate, vol);
    double d2 = d1 - vol * Math.sqrt(years);
    double discount = Math.exp(-rate * years);
    return right == Right.CE
        ? spot * Normal.cdf(d1) - strike * discount * Normal.cdf(d2)
        : strike * discount * Normal.cdf(-d2) - spot * Normal.cdf(-d1);
  }

  /** Delta per unit: between 0 and 1 for a call, between −1 and 0 for a put. */
  public static double delta(
      double spot, double strike, double years, double rate, double vol, Right right) {
    requirePositive(spot, strike);
    if (expired(vol, years)) {
      boolean inTheMoney = right == Right.CE ? spot > strike : spot < strike;
      return inTheMoney ? (right == Right.CE ? 1.0 : -1.0) : 0.0;
    }
    double nd1 = Normal.cdf(d1(spot, strike, years, rate, vol));
    return right == Right.CE ? nd1 : nd1 - 1.0;
  }

  /** Change in delta per 1-point move in the index (the same for calls and puts). */
  public static double gamma(double spot, double strike, double years, double rate, double vol) {
    requirePositive(spot, strike);
    if (expired(vol, years)) {
      return 0.0;
    }
    return Normal.pdf(d1(spot, strike, years, rate, vol)) / (spot * vol * Math.sqrt(years));
  }

  /** Change in price per 1 volatility point (0.01), per unit. */
  public static double vega(double spot, double strike, double years, double rate, double vol) {
    requirePositive(spot, strike);
    if (expired(vol, years)) {
      return 0.0;
    }
    return spot * Normal.pdf(d1(spot, strike, years, rate, vol)) * Math.sqrt(years) / 100;
  }

  /** Change in price per calendar day, per unit (negative for a long option). */
  public static double theta(
      double spot, double strike, double years, double rate, double vol, Right right) {
    requirePositive(spot, strike);
    if (expired(vol, years)) {
      return 0.0;
    }
    double d1 = d1(spot, strike, years, rate, vol);
    double d2 = d1 - vol * Math.sqrt(years);
    double decay = -spot * Normal.pdf(d1) * vol / (2 * Math.sqrt(years));
    double carry =
        rate
            * strike
            * Math.exp(-rate * years)
            * (right == Right.CE ? Normal.cdf(d2) : -Normal.cdf(-d2));
    return (decay - carry) / 365;
  }

  /**
   * The volatility that reproduces {@code price}, or empty when the price is at or below intrinsic
   * or can't be solved in [0.0001, 5] (a stale or illiquid quote). Brent's method, tolerance 1e-6.
   */
  public static OptionalDouble impliedVol(
      double price, double spot, double strike, double years, double rate, Right right) {
    requirePositive(spot, strike);
    if (years <= MIN_YEARS) {
      // At expiry every volatility gives the same (intrinsic) price, so there is no implied
      // volatility. The legacy
      // Python returned an arbitrary bound (0.0001) here; empty is the honest answer.
      return OptionalDouble.empty();
    }
    double discountedStrike = strike * Math.exp(-rate * years);
    double intrinsic =
        right == Right.CE
            ? Math.max(spot - discountedStrike, 0)
            : Math.max(discountedStrike - spot, 0);
    if (price <= 0 || price <= intrinsic + 1e-9) {
      return OptionalDouble.empty();
    }
    return Brent.root(
        v -> price(spot, strike, years, rate, v, right) - price, IV_LOW, IV_HIGH, 1e-6, 200);
  }

  private static boolean expired(double vol, double years) {
    return vol <= 0 || years <= MIN_YEARS;
  }

  private static double d1(double spot, double strike, double years, double rate, double vol) {
    return (Math.log(spot / strike) + (rate + vol * vol / 2) * years) / (vol * Math.sqrt(years));
  }

  private static void requirePositive(double spot, double strike) {
    if (spot <= 0 || strike <= 0) {
      throw new IllegalArgumentException("spot and strike must be positive");
    }
  }
}
