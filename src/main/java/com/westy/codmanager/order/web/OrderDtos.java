package com.westy.codmanager.order.web;

import com.westy.codmanager.geo.domain.Carrier;
import com.westy.codmanager.geo.domain.DeliveryType;
import com.westy.codmanager.order.domain.Order;
import com.westy.codmanager.order.domain.OrderItem;
import com.westy.codmanager.order.domain.OrderSource;
import com.westy.codmanager.order.domain.OrderStatus;
import com.westy.codmanager.order.domain.OrderStatusHistory;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class OrderDtos {

    private OrderDtos() {
    }

    public record OrderLineRequest(
            @NotNull UUID variantId,
            @Min(1) @Max(999) int quantity) {
    }

    public record CreateOrderRequest(
            @NotBlank @Size(max = 160) String customerName,
            /* Algerian mobile numbers: 05, 06 or 07 followed by eight digits. */
            @NotBlank @Pattern(regexp = "^0[5-7][0-9]{8}$",
                    message = "must be a valid Algerian mobile number") String phone,
            @NotNull @Min(1) @Max(58) Short wilayaCode,
            @NotBlank @Size(max = 120) String commune,
            @Size(max = 400) String address,
            @NotNull OrderSource source,
            @NotNull DeliveryType deliveryType,
            @NotNull Carrier carrier,
            @Size(max = 1000) String notes,
            @NotEmpty @Valid List<OrderLineRequest> items) {
    }

    public record TransitionRequest(
            @NotNull OrderStatus status,
            @Size(max = 400) String reason) {
    }

    public record OrderItemResponse(
            String productName,
            String variantSku,
            BigDecimal unitPrice,
            int quantity,
            BigDecimal lineTotal) {

        static OrderItemResponse from(OrderItem item) {
            return new OrderItemResponse(item.getProductName(), item.getVariantSku(),
                    item.getUnitPrice(), item.getQuantity(), item.lineTotal());
        }
    }

    public record HistoryEntryResponse(
            OrderStatus fromStatus,
            OrderStatus toStatus,
            String reason,
            Instant changedAt) {

        static HistoryEntryResponse from(OrderStatusHistory entry) {
            return new HistoryEntryResponse(entry.getFromStatus(), entry.getToStatus(),
                    entry.getReason(), entry.getChangedAt());
        }
    }

    public record OrderResponse(
            String id,
            String orderNumber,
            OrderStatus status,
            List<OrderStatus> nextStates,
            String customerName,
            String phone,
            Short wilayaCode,
            String commune,
            String address,
            OrderSource source,
            DeliveryType deliveryType,
            Carrier carrier,
            BigDecimal subtotal,
            BigDecimal deliveryFee,
            BigDecimal total,
            String notes,
            Instant confirmedAt,
            Instant deliveredAt,
            Instant createdAt,
            List<OrderItemResponse> items,
            List<HistoryEntryResponse> history) {

        public static OrderResponse from(Order order, List<OrderStatus> nextStates) {
            return new OrderResponse(
                    order.getId().toString(),
                    order.getOrderNumber(),
                    order.getStatus(),
                    nextStates,
                    order.getCustomer().getFullName(),
                    order.getCustomer().getPhone(),
                    order.getWilaya().getCode(),
                    order.getCommune(),
                    order.getAddress(),
                    order.getSource(),
                    order.getDeliveryType(),
                    order.getCarrier(),
                    order.getSubtotal(),
                    order.getDeliveryFee(),
                    order.getTotal(),
                    order.getNotes(),
                    order.getConfirmedAt(),
                    order.getDeliveredAt(),
                    order.getCreatedAt(),
                    order.getItems().stream().map(OrderItemResponse::from).toList(),
                    order.getHistory().stream().map(HistoryEntryResponse::from).toList());
        }
    }
}
