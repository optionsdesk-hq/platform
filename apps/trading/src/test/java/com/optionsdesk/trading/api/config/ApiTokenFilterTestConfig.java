package com.optionsdesk.trading.api.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;

/** Lets web-slice tests in other packages load the API's filter configuration. */
@TestConfiguration(proxyBeanMethods = false)
@Import(ApiConfiguration.class)
public class ApiTokenFilterTestConfig {}
