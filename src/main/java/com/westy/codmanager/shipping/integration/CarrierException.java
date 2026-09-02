package com.westy.codmanager.shipping.integration;

/**
 * Something went wrong talking to a carrier.
 *
 * The retryable flag is the important part: a timeout or a 503 is worth trying
 * again, while a rejected address or a bad API key will fail identically no
 * matter how many times it is sent.
 */
public class CarrierException extends RuntimeException {

    private final boolean retryable;

    public CarrierException(String message, boolean retryable) {
        super(message);
        this.retryable = retryable;
    }

    public CarrierException(String message, boolean retryable, Throwable cause) {
        super(message, cause);
        this.retryable = retryable;
    }

    public static CarrierException transientFailure(String message, Throwable cause) {
        return new CarrierUnavailableException(message, cause);
    }

    public static CarrierException transientFailure(String message) {
        return new CarrierUnavailableException(message);
    }

    public static CarrierException permanentFailure(String message) {
        return new CarrierException(message, false);
    }

    public boolean isRetryable() {
        return retryable;
    }
}
