package com.westy.codmanager.shipping.integration;

/**
 * A carrier failure worth trying again: a timeout, a 5xx, a rate limit.
 *
 * Retry is driven by the exception type rather than by a condition on
 * {@link CarrierException}. Spring Retry decides from the declared type before
 * it ever inspects the instance, so a separate class removes the guesswork —
 * and a reader can see which failures are retried from the throw site alone.
 */
public class CarrierUnavailableException extends CarrierException {

    public CarrierUnavailableException(String message, Throwable cause) {
        super(message, true, cause);
    }

    public CarrierUnavailableException(String message) {
        super(message, true);
    }
}
