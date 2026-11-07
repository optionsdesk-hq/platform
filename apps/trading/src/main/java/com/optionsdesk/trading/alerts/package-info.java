/**
 * Alerts: messages to the owner (Telegram). Any module sends through {@link
 * com.optionsdesk.trading.alerts.port.Alerts}; delivery is queued and retried in the background, so
 * an outage never blocks trading. No secrets or personal data in messages.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Alerts")
package com.optionsdesk.trading.alerts;
