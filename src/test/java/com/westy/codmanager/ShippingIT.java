package com.westy.codmanager;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.westy.codmanager.auth.repository.UserRepository;
import com.westy.codmanager.catalog.repository.ProductRepository;
import com.westy.codmanager.customer.repository.CustomerRepository;
import com.westy.codmanager.order.repository.OrderRepository;
import com.westy.codmanager.shipping.repository.ShipmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The carrier is replaced by a mock HTTP server, so these tests cover the parts
 * that actually break in production: rejected parcels, server errors, retries
 * and statuses nobody has mapped yet. No credentials, no network, no rate limit.
 */
@AutoConfigureMockMvc
class ShippingIT extends AbstractIntegrationTest {

    /*
     * Started in a static initialiser, not @BeforeAll.
     *
     * @DynamicPropertySource is evaluated while Spring builds the context,
     * which happens before @BeforeAll runs. Starting the server there means the
     * port is registered before anything can ask for it.
     */
    private static final WireMockServer carrier = new WireMockServer(options().dynamicPort());

    static {
        carrier.start();
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper mapper;

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
    private String variantId;

    @DynamicPropertySource
    static void carrierProperties(DynamicPropertyRegistry registry) {
        registry.add("app.carriers.yalidine.base-url", () -> carrier.baseUrl());
        registry.add("app.carriers.yalidine.api-id", () -> "test-id");
        registry.add("app.carriers.yalidine.api-token", () -> "test-token");
    }

    @BeforeEach
    void setUp() throws Exception {
        carrier.resetAll();

        shipments.deleteAll();
        orders.deleteAll();
        customers.deleteAll();
        products.deleteAll();
        users.deleteAll();

        String auth = mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"ship@example.com","password":"correct-horse",
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

        variantId = mapper.readTree(variant).get("id").asText();
    }

    private String packedOrder() throws Exception {
        String response = mvc.perform(post("/api/v1/orders").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerName":"Amine B.","phone":"0551234567","wilayaCode":25,
                                 "commune":"Constantine","address":"Cité Zouaghi","source":"INSTAGRAM",
                                 "deliveryType":"HOME","carrier":"YALIDINE",
                                 "items":[{"variantId":"%s","quantity":1}]}""".formatted(variantId)))
                .andReturn().getResponse().getContentAsString();

        String orderId = mapper.readTree(response).get("id").asText();

        move(orderId, "CONFIRMED");
        move(orderId, "PACKED");

        return orderId;
    }

    private void move(String orderId, String status) throws Exception {
        mvc.perform(post("/api/v1/orders/" + orderId + "/transitions")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"%s","reason":"test"}""".formatted(status)))
                .andExpect(status().isOk());
    }

    private void stubCreateSuccess(String orderNumber) {
        carrier.stubFor(WireMock.post(urlPathEqualTo("/parcels")).willReturn(okJson("""
                {"%s":{"success":true,"tracking":"yal-D-123","label":"https://label/x.pdf"}}"""
                .formatted(orderNumber))));
    }

    private String orderNumberOf(String orderId) throws Exception {
        String body = mvc.perform(get("/api/v1/orders/" + orderId).header("Authorization", token))
                .andReturn().getResponse().getContentAsString();

        return mapper.readTree(body).get("orderNumber").asText();
    }

    @Test
    void shippingAPackedOrderStoresTheTrackingNumberAndAdvancesTheOrder() throws Exception {
        String orderId = packedOrder();
        stubCreateSuccess(orderNumberOf(orderId));

        mvc.perform(post("/api/v1/orders/" + orderId + "/shipment").header("Authorization", token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.trackingNumber").value("yal-D-123"))
                .andExpect(jsonPath("$.labelUrl").value("https://label/x.pdf"));

        mvc.perform(get("/api/v1/orders/" + orderId).header("Authorization", token))
                .andExpect(jsonPath("$.status").value("SHIPPED"));
    }

    @Test
    void anOrderThatIsNotPackedCannotBeShipped() throws Exception {
        String response = mvc.perform(post("/api/v1/orders").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerName":"Amine B.","phone":"0551234567","wilayaCode":25,
                                 "commune":"Constantine","source":"INSTAGRAM","deliveryType":"HOME",
                                 "carrier":"YALIDINE",
                                 "items":[{"variantId":"%s","quantity":1}]}""".formatted(variantId)))
                .andReturn().getResponse().getContentAsString();

        mvc.perform(post("/api/v1/orders/" + mapper.readTree(response).get("id").asText()
                        + "/shipment").header("Authorization", token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NOT_PACKED"));
    }

    @Test
    void shippingTwiceDoesNotCreateASecondParcel() throws Exception {
        String orderId = packedOrder();
        String orderNumber = orderNumberOf(orderId);
        stubCreateSuccess(orderNumber);

        mvc.perform(post("/api/v1/orders/" + orderId + "/shipment").header("Authorization", token))
                .andExpect(status().isCreated());

        // The order is SHIPPED now, so a second call is refused before any HTTP.
        mvc.perform(post("/api/v1/orders/" + orderId + "/shipment").header("Authorization", token))
                .andExpect(status().isConflict());

        carrier.verify(1, postRequestedFor(urlPathEqualTo("/parcels")));
    }

    @Test
    void aServerErrorIsRetriedThreeTimes() throws Exception {
        String orderId = packedOrder();

        carrier.stubFor(WireMock.post(urlPathEqualTo("/parcels"))
                .willReturn(aResponse().withStatus(503)));

        mvc.perform(post("/api/v1/orders/" + orderId + "/shipment").header("Authorization", token))
                .andExpect(status().is5xxServerError());

        carrier.verify(3, postRequestedFor(urlPathEqualTo("/parcels")));
    }

    @Test
    void aRejectedAddressIsNotRetried() throws Exception {
        String orderId = packedOrder();
        String orderNumber = orderNumberOf(orderId);

        carrier.stubFor(WireMock.post(urlPathEqualTo("/parcels")).willReturn(okJson("""
                {"%s":{"success":false,"message":"commune inconnue"}}""".formatted(orderNumber))));

        mvc.perform(post("/api/v1/orders/" + orderId + "/shipment").header("Authorization", token))
                .andExpect(status().is5xxServerError());

        carrier.verify(1, postRequestedFor(urlPathEqualTo("/parcels")));
    }

    @Test
    void syncTranslatesTheCarrierStatusAndAdvancesTheOrder() throws Exception {
        String orderId = packedOrder();
        stubCreateSuccess(orderNumberOf(orderId));

        mvc.perform(post("/api/v1/orders/" + orderId + "/shipment").header("Authorization", token))
                .andExpect(status().isCreated());

        carrier.stubFor(WireMock.get(urlPathEqualTo("/parcels/yal-D-123")).willReturn(okJson("""
                {"data":[{"last_status":"Sorti en livraison","tracking":"yal-D-123"}]}""")));

        mvc.perform(post("/api/v1/orders/" + orderId + "/shipment/sync")
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.carrierStatus").value("Sorti en livraison"))
                .andExpect(jsonPath("$.events[0].mappedStatus").value("OUT_FOR_DELIVERY"));

        mvc.perform(get("/api/v1/orders/" + orderId).header("Authorization", token))
                .andExpect(jsonPath("$.status").value("OUT_FOR_DELIVERY"));
    }

    @Test
    void anUnknownCarrierStatusIsRecordedButChangesNothing() throws Exception {
        String orderId = packedOrder();
        stubCreateSuccess(orderNumberOf(orderId));

        mvc.perform(post("/api/v1/orders/" + orderId + "/shipment").header("Authorization", token));

        carrier.stubFor(WireMock.get(urlPathEqualTo("/parcels/yal-D-123")).willReturn(okJson("""
                {"data":[{"last_status":"Statut tout neuf"}]}""")));

        mvc.perform(post("/api/v1/orders/" + orderId + "/shipment/sync")
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.events[0].rawStatus").value("Statut tout neuf"))
                .andExpect(jsonPath("$.events[0].mappedStatus").doesNotExist());

        // The order is untouched: the carrier informs, it does not command.
        mvc.perform(get("/api/v1/orders/" + orderId).header("Authorization", token))
                .andExpect(jsonPath("$.status").value("SHIPPED"));
    }

    @Test
    void aDuplicateAtTheCarrierReusesTheExistingParcel() throws Exception {
        String orderId = packedOrder();
        String orderNumber = orderNumberOf(orderId);

        carrier.stubFor(WireMock.post(urlPathEqualTo("/parcels")).willReturn(okJson("""
                {"%s":{"success":false,"message":"Ce colis existe déjà","tracking":"yal-D-999"}}"""
                .formatted(orderNumber))));

        mvc.perform(post("/api/v1/orders/" + orderId + "/shipment").header("Authorization", token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.trackingNumber").value("yal-D-999"));
    }
}
