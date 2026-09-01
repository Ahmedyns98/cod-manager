package com.westy.codmanager.shipping.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * Verifies that an inbound webhook really came from the carrier.
 *
 * The endpoint has to be public, so anyone on the internet can post to it. The
 * shared secret and an HMAC over the raw body are what separate a real
 * notification from someone marking their own order as delivered.
 */
@Component
public class WebhookSignatureVerifier {

    private static final String ALGORITHM = "HmacSHA256";

    private final byte[] secret;

    public WebhookSignatureVerifier(@Value("${app.webhooks.secret}") String secret) {
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
    }

    public boolean isValid(String rawBody, String providedSignature) {
        if (providedSignature == null || providedSignature.isBlank()) {
            return false;
        }

        String expected = sign(rawBody);

        /*
         * Constant-time comparison. A plain equals() leaks how many leading
         * characters were right through its timing, which is enough to forge a
         * signature one byte at a time.
         */
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                providedSignature.trim().getBytes(StandardCharsets.UTF_8));
    }

    public String sign(String rawBody) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret, ALGORITHM));

            return HexFormat.of().formatHex(mac.doFinal(rawBody.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("Cannot compute webhook signature", ex);
        }
    }

    /** Stable fingerprint of a payload, used to detect repeat deliveries. */
    public String fingerprint(String rawBody) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            return HexFormat.of().formatHex(
                    digest.digest(rawBody.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("Cannot fingerprint webhook payload", ex);
        }
    }
}
