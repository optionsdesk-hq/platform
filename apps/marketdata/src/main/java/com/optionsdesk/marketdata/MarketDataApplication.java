package com.optionsdesk.marketdata;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** The market-data app. Modules to come: recorder, ingest, dataquality (see docs/lld.md). */
@SpringBootApplication
public class MarketDataApplication {

  public static void main(String[] args) {
    SpringApplication.run(MarketDataApplication.class, args);
  }
}
