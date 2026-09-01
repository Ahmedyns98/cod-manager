package com.westy.codmanager.shipping.web;

import com.westy.codmanager.geo.domain.Carrier;
import com.westy.codmanager.shipping.service.WebhookService;
import com.westy.codmanager.shipping.service.WebhookSignatureVerifier;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Public endpoint: the carrier has no token from us, only the shared secret.
 *
 * Every outcome that is not a forged signature answers 200. A carrier that
 * receives an error will keep resending the same notification for hours, so
 * "received and understood, nothing to do" must not look like a failure.
 */
@RestController
@RequestMapping("/api/v1/webhooks")
@Tag(name = "Webhooks")
public class WebhookController {

    private final WebhookService service;
    private final WebhookSignatureVerifier verifier;

    public WebhookController(WebhookService service, WebhookSignatureVerifier verifier) {
        this.service = service;
        this.verifier = verifier;
    }

    @PostMapping("/yalidine")
    @Operation(summary = "Receive a Yalidine status notification")
    public ResponseEntity<Map<String, String>> yalidine(
            @RequestBody String rawBody,
            @RequestHeader(value = "X-Signature", required = false) String signature) {

        if (!verifier.isValid(rawBody, signature)) {
            return ResponseEntity.status(401).body(Map.of("status", "invalid signature"));
        }

        WebhookService.Result result = service.handle(Carrier.YALIDINE, rawBody);

        return ResponseEntity.ok(Map.of("status", result.name().toLowerCase()));
    }
}
