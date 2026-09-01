package com.westy.codmanager.shipping.integration;

import org.springframework.stereotype.Component;

/**
 * Referenced by name from the @Retryable expressions so the retry decision
 * lives in one testable place rather than being duplicated across annotations.
 */
@Component("carrierRetryPolicy")
public class CarrierRetryPolicy {

    public boolean shouldRetry(Throwable throwable) {
        return throwable instanceof CarrierException carrier && carrier.isRetryable();
    }
}
