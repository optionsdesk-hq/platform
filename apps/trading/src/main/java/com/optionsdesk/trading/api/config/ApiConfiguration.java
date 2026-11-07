package com.optionsdesk.trading.api.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** API wiring: the write-token filter on {@code /api/*}. */
@Configuration(proxyBeanMethods = false)
class ApiConfiguration {

  @Bean
  FilterRegistrationBean<ApiTokenFilter> apiTokenFilter(
      @Value("${optionsdesk.api.token:}") String token) {
    var registration = new FilterRegistrationBean<>(new ApiTokenFilter(token));
    registration.addUrlPatterns("/api/*");
    registration.setName("apiTokenFilter");
    return registration;
  }
}
