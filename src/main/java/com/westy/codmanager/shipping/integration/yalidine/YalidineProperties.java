package com.westy.codmanager.shipping.integration.yalidine;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Credentials and tuning for the Yalidine API. Bound from app.carriers.yalidine
 * so the base URL can be pointed at a mock server during tests.
 */
@ConfigurationProperties(prefix = "app.carriers.yalidine")
public record YalidineProperties(
        String baseUrl,
        String apiId,
        String apiToken,
        String fromWilayaName,
        Duration timeout) {

    public YalidineProperties {
        if (timeout == null) {
            timeout = Duration.ofSeconds(10);
        }
    }
}
