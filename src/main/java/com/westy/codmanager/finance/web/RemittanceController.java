package com.westy.codmanager.finance.web;

import com.westy.codmanager.finance.service.ReconciliationService;
import com.westy.codmanager.finance.web.RemittanceDtos.RemittanceResponse;
import com.westy.codmanager.geo.domain.Carrier;
import com.westy.codmanager.order.web.OrderDtos.OrderResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/remittances")
@Tag(name = "Finance")
public class RemittanceController {

    private final ReconciliationService service;

    public RemittanceController(ReconciliationService service) {
        this.service = service;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload a carrier payout export and reconcile it")
    public ResponseEntity<RemittanceResponse> upload(
            @AuthenticationPrincipal String ownerId,
            @RequestParam Carrier carrier,
            @RequestParam String reference,
            @RequestParam BigDecimal declaredTotal,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate receivedAt,
            @RequestParam("file") MultipartFile file) {

        RemittanceResponse body = RemittanceResponse.from(service.importPayout(
                UUID.fromString(ownerId), carrier, reference, declaredTotal, receivedAt, file));

        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @GetMapping
    @Operation(summary = "List imported payouts")
    public List<RemittanceResponse> list(@AuthenticationPrincipal String ownerId,
                                         @PageableDefault(size = 20) Pageable pageable) {
        return service.list(UUID.fromString(ownerId), pageable).stream()
                .map(RemittanceResponse::summary)
                .toList();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Fetch a payout with every reconciled line")
    public RemittanceResponse get(@AuthenticationPrincipal String ownerId,
                                  @PathVariable UUID id) {
        return RemittanceResponse.from(service.get(UUID.fromString(ownerId), id));
    }

    @GetMapping("/pending")
    @Operation(summary = "Delivered orders the carrier has not paid out yet")
    public Page<OrderResponse> pending(@AuthenticationPrincipal String ownerId,
                                       @PageableDefault(size = 50) Pageable pageable) {
        return service.pendingPayout(UUID.fromString(ownerId), pageable)
                .map(order -> OrderResponse.from(order, List.of()));
    }
}
