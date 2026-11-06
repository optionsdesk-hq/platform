package com.optionsdesk.trading.kite;

/** Kite refused a call or couldn't be reached. Messages never contain the key or the token. */
public final class KiteException extends RuntimeException {
  private static final long serialVersionUID = 1L;

  /** What went wrong. */
  public enum Kind {
    /** No token for today, or Kite rejected it: the daily login is needed. */
    SESSION,
    /** Kite answered with an error (input, permission, rate limit, …). */
    API,
    /** Kite couldn't be reached, or the answer couldn't be read. */
    NETWORK
  }

  private final Kind kind;

  public KiteException(Kind kind, String message) {
    super(message);
    this.kind = kind;
  }

  public KiteException(Kind kind, String message, Throwable cause) {
    super(message, cause);
    this.kind = kind;
  }

  public Kind kind() {
    return kind;
  }
}
