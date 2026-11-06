package com.optionsdesk.trading;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * The trading app. Each package directly below {@code com.optionsdesk.trading} is a Spring Modulith
 * application module (ops, api, and later marketdata, signals, strategy, risk, book, execution,
 * journal, alerts): a module's root package is its public API; sub-packages are internal. {@code
 * ModularityTest} verifies the boundaries.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class TradingApplication {

  public static void main(String[] args) {
    SpringApplication.run(TradingApplication.class, args);
  }
}
