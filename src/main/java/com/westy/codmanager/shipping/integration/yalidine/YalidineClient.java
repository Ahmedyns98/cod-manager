package com.westy.codmanager.shipping.integration.yalidine;

import com.fasterxml.jackson.databind.JsonNode;
import com.westy.codmanager.geo.domain.Carrier;
import com.westy.codmanager.geo.domain.DeliveryType;
import com.westy.codmanager.order.domain.Order;
import com.westy.codmanager.order.domain.OrderItem;
import com.westy.codmanager.shipping.integration.CarrierClient;
import com.westy.codmanager.shipping.integration.CarrierException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Yalidine implementation of {@link CarrierClient}.
 *
 * Only transient failures are retried, three times with exponential backoff.
 * A rejected address or an invalid key is thrown straight through: retrying a
 * request the carrier has already refused just wastes the seller's time.
 */
@Component
public class YalidineClient implements CarrierClient {

    private static final Logger log = LoggerFactory.getLogger(YalidineClient.class);

    private final RestClient http;
    private final YalidineProperties properties;

    public YalidineClient(RestClient.Builder builder, YalidineProperties properties) {
        this.properties = properties;
        this.http = builder
                .baseUrl(properties.baseUrl())
                .defaultHeader("X-API-ID", properties.apiId())
                .defaultHeader("X-API-TOKEN", properties.apiToken())
                .build();
    }

    @Override
    public Carrier carrier() {
        return Carrier.YALIDINE;
    }

    @Override
    @Retryable(retryFor = CarrierException.class, maxAttempts = 3,
            backoff = @Backoff(delay = 500, multiplier = 2.0),
            noRetryFor = {}, exceptionExpression = "#{@carrierRetryPolicy.shouldRetry(#root)}")
    public ParcelCreated createParcel(Order order) {
        Map<String, Object> payload = toPayload(order);

        JsonNode response = post("/parcels", List.of(payload));
        JsonNode parcel = parcelNode(response, order.getOrderNumber());

        if (parcel.path("success").asBoolean(false)) {
            return new ParcelCreated(
                    parcel.path("tracking").asText(),
                    parcel.path("label").asText(null));
        }

        String message = parcel.path("message").asText("Yalidine rejected the parcel");

        /*
         * A duplicate is not an error. It means an earlier attempt reached the
         * carrier and only the response was lost, so the existing tracking
         * number is the correct answer.
         */
        if (message.toLowerCase().contains("existe") || message.toLowerCase().contains("duplicate")) {
            log.info("Parcel {} already registered at Yalidine, reusing it", order.getOrderNumber());
            return new ParcelCreated(parcel.path("tracking").asText(), null);
        }

        throw CarrierException.permanentFailure(message);
    }

    @Override
    @Retryable(retryFor = CarrierException.class, maxAttempts = 3,
            backoff = @Backoff(delay = 500, multiplier = 2.0),
            exceptionExpression = "#{@carrierRetryPolicy.shouldRetry(#root)}")
    public ParcelStatus fetchStatus(String trackingNumber) {
        JsonNode response = get("/parcels/" + trackingNumber);
        JsonNode parcel = response.path("data").isArray() && !response.path("data").isEmpty()
                ? response.path("data").get(0)
                : response;

        String status = parcel.path("last_status").asText(null);

        if (status == null) {
            throw CarrierException.permanentFailure("No status for parcel " + trackingNumber);
        }

        return new ParcelStatus(status, parcel.toString());
    }

    @Override
    public void cancelParcel(String trackingNumber) {
        try {
            http.delete()
                    .uri("/parcels/{tracking}", trackingNumber)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, response) -> {
                        throw classify(response.getStatusCode(), "cancel");
                    })
                    .toBodilessEntity();
        } catch (ResourceAccessException ex) {
            throw CarrierException.transientFailure("Yalidine unreachable while cancelling", ex);
        }
    }

    /*
     * Yalidine keys its response by the reference we sent. Falling back to the
     * single entry when that key is missing keeps one parcel per request
     * working even if the carrier echoes the reference back in a slightly
     * different form, which is cheaper than failing a real shipment over it.
     */
    private JsonNode parcelNode(JsonNode response, String orderNumber) {
        JsonNode byReference = response.path(orderNumber);

        if (!byReference.isMissingNode() && !byReference.isNull()) {
            return byReference;
        }

        if (response.isObject() && response.size() == 1) {
            return response.fields().next().getValue();
        }

        return response;
    }

    private Map<String, Object> toPayload(Order order) {
        String description = order.getItems().stream()
                .map(item -> "%s x%d".formatted(item.getProductName(), item.getQuantity()))
                .collect(Collectors.joining(", "));

        int totalItems = order.getItems().stream().mapToInt(OrderItem::getQuantity).sum();

        return Map.of(
                // Our own order number travels as the carrier's reference: it is
                // what makes a retried create idempotent on their side.
                "order_id", order.getOrderNumber(),
                "firstname", order.getCustomer().getFullName(),
                "familyname", "",
                "contact_phone", order.getCustomer().getPhone(),
                "address", order.getAddress() == null ? order.getCommune() : order.getAddress(),
                "to_wilaya_name", order.getWilaya().getNameFr(),
                "to_commune_name", order.getCommune(),
                "product_list", description,
                "price", order.getTotal().intValue(),
                "is_stopdesk", order.getDeliveryType() == DeliveryType.STOPDESK);
    }

    private JsonNode post(String path, Object body) {
        try {
            return http.post()
                    .uri(path)
                    .body(body)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, response) -> {
                        throw classify(response.getStatusCode(), "create");
                    })
                    .body(JsonNode.class);
        } catch (ResourceAccessException ex) {
            throw CarrierException.transientFailure("Yalidine unreachable", ex);
        }
    }

    private JsonNode get(String path) {
        try {
            return http.get()
                    .uri(path)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, response) -> {
                        throw classify(response.getStatusCode(), "status");
                    })
                    .body(JsonNode.class);
        } catch (ResourceAccessException ex) {
            throw CarrierException.transientFailure("Yalidine unreachable", ex);
        }
    }

    /**
     * 5xx and 429 are the carrier's problem and will likely pass. 4xx is our
     * problem and will not.
     */
    private CarrierException classify(HttpStatusCode status, String operation) {
        boolean worthRetrying = status.is5xxServerError() || status.value() == 429;

        String message = "Yalidine %s failed with HTTP %d".formatted(operation, status.value());

        return new CarrierException(message, worthRetrying);
    }
}
