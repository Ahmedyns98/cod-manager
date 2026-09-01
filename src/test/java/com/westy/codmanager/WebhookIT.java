package com.westy.codmanager;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.westy.codmanager.auth.repository.UserRepository;
import com.westy.codmanager.catalog.repository.ProductRepository;
import com.westy.codmanager.customer.repository.CustomerRepository;
import com.westy.codmanager.order.repository.OrderRepository;
import com.westy.codmanager.shipping.repository.ShipmentRepository;
import com.westy.codmanager.shipping.repository.WebhookEventRepository;
import com.westy.codmanager.shipping.service.WebhookSignatureVerifier;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class WebhookIT extends AbstractIntegrationTest {

    private static WireMockServer carrier;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper mapper;

    @Autowired
    private WebhookSignatureVerifier verifier;

    @Autowired
    private WebhookEventRepository webhookEvents;

    @Autowired
    private ShipmentRepository shipments;

    @Autowired
    private OrderRepository orders;

    @Autowired
    private CustomerRepository customers;

    @Autowired
    private ProductRepository products;

    @Autowired
    private UserRepository users;

    private String token;
    private String orderId;

    @BeforeAll
    static void startCarrier() {
        carrier = new WireMockServer(options().dynamicPort());
        carrier.start();
    }

    @AfterAll
    static void stopCarrier() {
        carrier.stop();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("app.carriers.yalidine.base-url", () -> carrier.baseUrl());
        registry.add("app.carriers.yalidine.api-id", () -> "test-id");
        registry.add("app.carriers.yalidine.api-token", () -> "test-token");
        // Push the scheduler well past the life of the test run.
        registry.add("app.sync.interval", () -> "PT24H");
    }

    @BeforeEach
    void setUp() throws Exception {
        carrier.resetAll();

        webhookEvents.deleteAll();
        shipments.deleteAll();
        orders.deleteAll();
        customers.deleteAll();
        products.deleteAll();
        users.deleteAll();

        String auth = mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"hook@example.com","password":"correct-horse",
                                 "storeName":"Boutique"}"""))
                .andReturn().getResponse().getContentAsString();

        token = "Bearer " + mapper.readTree(auth).get("accessToken").asText();

        String product = mvc.perform(post("/api/v1/products").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"T-shirt","sku":"TS-1","basePrice":2500.00,"costPrice":1200.00}"""))
                .andReturn().getResponse().getContentAsString();

        String variant = mvc.perform(post("/api/v1/products/"
                        + mapper.readTree(product).get("id").asText() + "/variants")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"size":"L","color":"Noir","sku":"TS-1-L","stockQty":10}"""))
                .andReturn().getResponse().getContentAsString();

        String order = mvc.perform(post("/api/v1/orders").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerName":"Amine B.","phone":"0551234567","wilayaCode":25,
                                 "commune":"Constantine","address":"Cité Zouaghi","source":"INSTAGRAM",
                                 "deliveryType":"HOME","carrier":"YALIDINE",
                                 "items":[{"variantId":"%s","quantity":1}]}"""
                                .formatted(mapper.readTree(variant).get("id").asText())))
                .andReturn().getResponse().getContentAsString();

        orderId = mapper.readTree(order).get("id").asText();
        String orderNumber = mapper.readTree(order).get("orderNumber").asText();

        move("CONFIRMED");
        move("PACKED");

        carrier.stubFor(post(urlPathEqualTo("/parcels")).willReturn(okJson("""
                {"%s":{"success":true,"tracking":"yal-D-123","label":"https://label/x.pdf"}}"""
                .formatted(orderNumber))));

        mvc.perform(post("/api/v1/orders/" + orderId + "/shipment").header("Authorization", token))
                .andExpect(status().isCreated());
    }

    private void move(String status) throws Exception {
        mvc.perform(post("/api/v1/orders/" + orderId + "/transitions")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"%s","reason":"test"}""".formatted(status)))
                .andExpect(status().isOk());
    }

    private void send(String body, String signature, int expectedStatus, String expectedResult)
            throws Exception {
        var request = post("/api/v1/webhooks/yalidine")
                .contentType(MediaType.APPLICATION_JSON).content(body);

        if (signature != null) {
            request = request.header("X-Signature", signature);
        }

        mvc.perform(request)
                .andExpect(status().is(expectedStatus))
                .andExpect(jsonPath("$.status").value(expectedResult));
    }

    @Test
    void aSignedNotificationAdvancesTheOrder() throws Exception {
        String body = """
                {"tracking":"yal-D-123","status":"Sorti en livraison"}""";

        send(body, verifier.sign(body), 200, "applied");

        mvc.perform(get("/api/v1/orders/" + orderId).header("Authorization", token))
                .andExpect(jsonPath("$.status").value("OUT_FOR_DELIVERY"));
    }

    @Test
    void anUnsignedNotificationIsRejected() throws Exception {
        String body = """
                {"tracking":"yal-D-123","status":"Livré"}""";

        send(body, null, 401, "invalid signature");

        // Nothing was written: an unverified caller cannot even fill the table.
        assertThat(webhookEvents.count()).isZero();
    }

    @Test
    void aForgedSignatureIsRejected() throws Exception {
        String body = """
                {"tracking":"yal-D-123","status":"Livré"}""";

        send(body, "deadbeef", 401, "invalid signature");

        mvc.perform(get("/api/v1/orders/" + orderId).header("Authorization", token))
                .andExpect(jsonPath("$.status").value("SHIPPED"));
    }

    @Test
    void theSameNotificationTwiceIsAppliedOnce() throws Exception {
        String body = """
                {"tracking":"yal-D-123","status":"Sorti en livraison"}""";
        String signature = verifier.sign(body);

        send(body, signature, 200, "applied");
        send(body, signature, 200, "duplicate");

        assertThat(webhookEvents.count()).isEqualTo(1);

        mvc.perform(get("/api/v1/orders/" + orderId + "/shipment").header("Authorization", token))
                .andExpect(jsonPath("$.events.length()").value(1));
    }

    @Test
    void anUnknownTrackingNumberIsRecordedAndAnswered200() throws Exception {
        String body = """
                {"tracking":"someone-elses-parcel","status":"Livré"}""";

        // 200, not an error: a 4xx would make the carrier resend for hours.
        send(body, verifier.sign(body), 200, "ignored");

        assertThat(webhookEvents.count()).isEqualTo(1);
    }

    @Test
    void aStatusTheStateMachineForbidsIsRecordedButNotApplied() throws Exception {
        String body = """
                {"tracking":"yal-D-123","status":"Livré"}""";

        // SHIPPED -> DELIVERED is not a legal jump; the parcel must go via transit.
        send(body, verifier.sign(body), 200, "applied");

        mvc.perform(get("/api/v1/orders/" + orderId).header("Authorization", token))
                .andExpect(jsonPath("$.status").value("SHIPPED"));

        mvc.perform(get("/api/v1/orders/" + orderId + "/shipment").header("Authorization", token))
                .andExpect(jsonPath("$.events[0].rawStatus").value("Livré"))
                .andExpect(jsonPath("$.events[0].mappedStatus").value("DELIVERED"));
    }

    @Test
    void malformedJsonIsStoredAndAnswered200() throws Exception {
        String body = "not json at all";

        send(body, verifier.sign(body), 200, "ignored");

        assertThat(webhookEvents.count()).isEqualTo(1);
    }
}
