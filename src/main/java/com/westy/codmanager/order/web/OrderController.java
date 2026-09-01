package com.westy.codmanager.order.web;

import com.westy.codmanager.order.domain.Order;
import com.westy.codmanager.order.domain.OrderStatus;
import com.westy.codmanager.order.service.OrderService;
import com.westy.codmanager.order.web.OrderDtos.CreateOrderRequest;
import com.westy.codmanager.order.web.OrderDtos.OrderResponse;
import com.westy.codmanager.order.web.OrderDtos.TransitionRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/orders")
@Tag(name = "Orders")
public class OrderController {

    private final OrderService service;

    public OrderController(OrderService service) {
        this.service = service;
    }

    @PostMapping
    @Operation(summary = "Create an order and price it for the destination wilaya")
    public ResponseEntity<OrderResponse> create(@AuthenticationPrincipal String ownerId,
                                                @Valid @RequestBody CreateOrderRequest request) {
        Order order = service.create(UUID.fromString(ownerId), request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(OrderResponse.from(order, service.nextStates(order)));
    }

    @GetMapping
    @Operation(summary = "List orders, optionally filtered by status")
    public Page<OrderResponse> list(@AuthenticationPrincipal String ownerId,
                                    @RequestParam(required = false) OrderStatus status,
                                    @PageableDefault(size = 20, sort = "createdAt",
                                            direction = Sort.Direction.DESC) Pageable pageable) {
        return service.list(UUID.fromString(ownerId), status, pageable)
                .map(order -> OrderResponse.from(order, service.nextStates(order)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Fetch one order with its lines and status history")
    public OrderResponse get(@AuthenticationPrincipal String ownerId, @PathVariable UUID id) {
        Order order = service.get(UUID.fromString(ownerId), id);
        return OrderResponse.from(order, service.nextStates(order));
    }

    @PostMapping("/{id}/transitions")
    @Operation(summary = "Move an order to its next status")
    public OrderResponse transition(@AuthenticationPrincipal String ownerId,
                                    @PathVariable UUID id,
                                    @Valid @RequestBody TransitionRequest request) {
        Order order = service.transition(UUID.fromString(ownerId), id,
                request.status(), request.reason());

        return OrderResponse.from(order, service.nextStates(order));
    }
}
