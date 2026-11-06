package com.optionsdesk.trading;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * A real PostgreSQL in Docker for tests (Testcontainers); Spring Boot wires the datasource to it
 * automatically ({@code @ServiceConnection}). Tests using it are skipped where Docker isn't
 * available and always run in CI.
 */
@TestConfiguration(proxyBeanMethods = false)
public class PostgresTestConfiguration {

  @Bean
  @ServiceConnection
  PostgreSQLContainer<?> postgres() {
    return new PostgreSQLContainer<>("postgres:16-alpine");
  }
}
