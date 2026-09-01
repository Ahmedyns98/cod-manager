package com.westy.codmanager;

import com.westy.codmanager.shipping.service.WebhookSignatureVerifier;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WebhookSignatureVerifierTest {

    private final WebhookSignatureVerifier verifier =
            new WebhookSignatureVerifier("test-secret");

    private static final String BODY = """
            {"tracking":"yal-D-1","status":"Livré"}""";

    @Test
    void aSignatureFromTheSharedSecretIsAccepted() {
        assertThat(verifier.isValid(BODY, verifier.sign(BODY))).isTrue();
    }

    @Test
    void aSignatureFromADifferentSecretIsRejected() {
        String forged = new WebhookSignatureVerifier("wrong-secret").sign(BODY);

        assertThat(verifier.isValid(BODY, forged)).isFalse();
    }

    @Test
    void changingOneCharacterOfTheBodyInvalidatesTheSignature() {
        String signature = verifier.sign(BODY);
        String tampered = BODY.replace("Livré", "Retourné");

        assertThat(verifier.isValid(tampered, signature)).isFalse();
    }

    @Test
    void anAbsentSignatureIsRejected() {
        assertThat(verifier.isValid(BODY, null)).isFalse();
        assertThat(verifier.isValid(BODY, "  ")).isFalse();
    }

    @Test
    void identicalPayloadsShareAFingerprint() {
        assertThat(verifier.fingerprint(BODY)).isEqualTo(verifier.fingerprint(BODY));
        assertThat(verifier.fingerprint(BODY)).isNotEqualTo(verifier.fingerprint(BODY + " "));
    }
}
