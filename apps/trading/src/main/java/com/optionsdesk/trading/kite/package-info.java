/**
 * The Kite Connect adapter: session check, quotes, instruments, historical candles and basket
 * margins over Kite's REST API (v3). The only module that talks to the broker (ArchUnit enforces
 * it).
 *
 * <p>Security: the API key comes from the environment ({@code KITE_API_KEY}); the day's access
 * token is <b>read</b> from the token file the login flow writes (it is never written here, never
 * logged, and the auto-login secrets are never read). The token is valid for the trading day in the
 * file; Kite can still cancel it, so {@link com.optionsdesk.trading.kite.KiteClient#tokenState()}
 * asks Kite.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Kite adapter")
package com.optionsdesk.trading.kite;
