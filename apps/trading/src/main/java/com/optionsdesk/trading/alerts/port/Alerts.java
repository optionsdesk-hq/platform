package com.optionsdesk.trading.alerts.port;

/**
 * Tell the owner something. Returns at once and never throws: delivery happens in the background.
 * Texts must not contain secrets or personal data.
 */
public interface Alerts {

  /** Something to know (entries, exits, summaries). */
  void info(String text);

  /** Something to act on (a book stopped, no market data, a failed order). */
  void urgent(String text);

  /** Alerts that go nowhere (tests, or when alerts are switched off). */
  Alerts NONE =
      new Alerts() {
        @Override
        public void info(String text) {}

        @Override
        public void urgent(String text) {}
      };
}
