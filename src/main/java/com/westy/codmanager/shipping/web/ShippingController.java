package com.westy.codmanager.shipping.web;

import com.westy.codmanager.shipping.service.ShippingService;
import com.westy.codmanager.shipping.web.ShippingDtos.ShipmentResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/orders/{orderId}/shipment")
@Tag(name = "Shipping")
public class ShippingController {

    private final ShippingService service;

    public ShippingController(ShippingService service) {
        this.service = service;
    }

    @PostMapping
    @Operation(summary = "Register a packed order with its carrier")
    public ResponseEntity<ShipmentResponse> ship(@AuthenticationPrincipal String ownerId,
                                                 @PathVariable UUID orderId) {
        ShipmentResponse body = ShipmentResponse.from(
                service.ship(UUID.fromString(ownerId), orderId));

        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @GetMapping
    @Operation(summary = "Fetch the shipment and its carrier event history")
    public ShipmentResponse get(@AuthenticationPrincipal String ownerId,
                                @PathVariable UUID orderId) {
        return ShipmentResponse.from(service.get(UUID.fromString(ownerId), orderId));
    }

    @PostMapping("/sync")
    @Operation(summary = "Pull the latest status from the carrier")
    public ShipmentResponse sync(@AuthenticationPrincipal String ownerId,
                                 @PathVariable UUID orderId) {
        return ShipmentResponse.from(service.sync(UUID.fromString(ownerId), orderId));
    }
}
