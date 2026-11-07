package com.optionsdesk.cli;

import java.util.Optional;

/** Entry point: reads OD_URL and OD_TOKEN from the environment and runs one command. */
public final class OdApplication {

  private OdApplication() {}

  public static void main(String[] args) {
    String url = Optional.ofNullable(System.getenv("OD_URL")).orElse("http://localhost:8080");
    var api = new ApiClient(url, Optional.ofNullable(System.getenv("OD_TOKEN")));
    System.exit(new Od(api, System.out).run(args));
  }
}
