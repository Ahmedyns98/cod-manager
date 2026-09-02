package com.westy.codmanager;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.westy.codmanager.auth.repository.UserRepository;
import com.westy.codmanager.catalog.repository.ProductRepository;
import com.westy.codmanager.customer.repository.CustomerRepository;
import com.westy.codmanager.finance.repository.RemittanceRepository;
import com.westy.codmanager.order.repository.OrderRepository;
import com.westy.codmanager.shipping.repository.ShipmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;

import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class RemittanceIT extends AbstractIntegrationTest {

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
    private RemittanceRepository remittances;

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
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("app.carriers.yalidine.base-url", () -> carrier.baseUrl());
        registry.add("app.carriers.yalidine.api-id", () -> "test-id");
        registry.add("app.carriers.yalidine.api-token", () -> "test-token");
        registry.add("app.sync.interval", () -> "PT24H");
    }

    @BeforeEach
    void setUp() throws Exception {
        carrier.resetAll();

        remittances.deleteAll();
        shipments.deleteAll();
        orders.deleteAll();
        customers.deleteAll();
        products.deleteAll();
        users.deleteAll();

        String auth = mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"money@example.com","password":"correct-horse",
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
                                {"size":"L","color":"Noir","sku":"TS-1-L","stockQty":100}"""))
                .andReturn().getResponse().getContentAsString();

        variantId = mapper.readTree(variant).get("id").asText();
    }

    /** Walks an order all the way to DELIVERED and returns its tracking number. */
    private String deliveredOrder(String tracking, int quantity) throws Exception {
        String order = mvc.perform(post("/api/v1/orders").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerName":"Amine B.","phone":"0551234567","wilayaCode":25,
                                 "commune":"Constantine","address":"Cité Zouaghi","source":"INSTAGRAM",
                                 "deliveryType":"HOME","carrier":"YALIDINE",
                                 "items":[{"variantId":"%s","quantity":%d}]}"""
                                .formatted(variantId, quantity)))
                .andReturn().getResponse().getContentAsString();

        String orderId = mapper.readTree(order).get("id").asText();
        String orderNumber = mapper.readTree(order).get("orderNumber").asText();

        move(orderId, "CONFIRMED");
        move(orderId, "PACKED");

        carrier.stubFor(WireMock.post(urlPathEqualTo("/parcels")).willReturn(okJson("""
                {"%s":{"success":true,"tracking":"%s","label":"https://label/x.pdf"}}"""
                .formatted(orderNumber, tracking))));

        mvc.perform(post("/api/v1/orders/" + orderId + "/shipment").header("Authorization", token))
                .andExpect(status().isCreated());

        move(orderId, "IN_TRANSIT");
        move(orderId, "OUT_FOR_DELIVERY");
        move(orderId, "DELIVERED");

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

    private MockMultipartFile csv(String content) {
        return new MockMultipartFile("file", "payout.csv", "text/csv",
                content.getBytes(StandardCharsets.UTF_8));
    }

    private org.springframework.test.web.servlet.ResultActions upload(
            String reference, String total, MockMultipartFile file) throws Exception {

        return mvc.perform(multipart("/api/v1/remittances")
                .file(file)
                .header("Authorization", token)
                .param("carrier", "YALIDINE")
                .param("reference", reference)
                .param("declaredTotal", total)
                .param("receivedAt", "2026-09-01"));
    }

    @Test
    void aMatchingPayoutSettlesTheOrder() throws Exception {
        String orderId = deliveredOrder("yal-1", 1);

        upload("VRS-001", "3000.00", csv("""
                tracking,collected,fee
                yal-1,3000.00,500.00"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.matchedCount").value(1))
                .andExpect(jsonPath("$.unaccounted").value(500.00))
                .andExpect(jsonPath("$.lines[0].status").value("SETTLED"));

        mvc.perform(get("/api/v1/orders/" + orderId).header("Authorization", token))
                .andExpect(jsonPath("$.status").value("SETTLED"));
    }

    @Test
    void aShortPaymentIsFlaggedAndTheOrderIsLeftAlone() throws Exception {
        String orderId = deliveredOrder("yal-2", 1);

        upload("VRS-002", "2500.00", csv("""
                tracking,collected,fee
                yal-2,2000.00,500.00"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.matchedCount").value(0))
                .andExpect(jsonPath("$.lines[0].status").value("AMOUNT_MISMATCH"))
                .andExpect(jsonPath("$.lines[0].expectedAmount").value(3000.00))
                .andExpect(jsonPath("$.lines[0].collectedAmount").value(2000.00));

        // Nothing was settled, so the money is still visibly outstanding.
        mvc.perform(get("/api/v1/orders/" + orderId).header("Authorization", token))
                .andExpect(jsonPath("$.status").value("DELIVERED"));
    }

    @Test
    void anUnknownTrackingNumberIsRecordedNotDropped() throws Exception {
        upload("VRS-003", "1000.00", csv("""
                tracking,collected
                someone-elses-parcel,1000.00"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.lineCount").value(1))
                .andExpect(jsonPath("$.matchedCount").value(0))
                .andExpect(jsonPath("$.lines[0].status").value("UNKNOWN_TRACKING"));
    }

    @Test
    void anOrderThatWasNeverDeliveredIsFlagged() throws Exception {
        String order = mvc.perform(post("/api/v1/orders").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerName":"Amine B.","phone":"0551234567","wilayaCode":25,
                                 "commune":"Constantine","source":"INSTAGRAM","deliveryType":"HOME",
                                 "carrier":"YALIDINE",
                                 "items":[{"variantId":"%s","quantity":1}]}""".formatted(variantId)))
                .andReturn().getResponse().getContentAsString();

        String orderId = mapper.readTree(order).get("id").asText();
        String orderNumber = mapper.readTree(order).get("orderNumber").asText();

        move(orderId, "CONFIRMED");
        move(orderId, "PACKED");

        carrier.stubFor(WireMock.post(urlPathEqualTo("/parcels")).willReturn(okJson("""
                {"%s":{"success":true,"tracking":"yal-4","label":null}}""".formatted(orderNumber))));

        mvc.perform(post("/api/v1/orders/" + orderId + "/shipment").header("Authorization", token));

        upload("VRS-004", "3000.00", csv("""
                tracking,collected
                yal-4,3000.00"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.lines[0].status").value("NOT_DELIVERED"));
    }

    @Test
    void thesameOrderCannotBePaidTwice() throws Exception {
        deliveredOrder("yal-5", 1);

        upload("VRS-005", "3000.00", csv("tracking,collected\nyal-5,3000.00"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.lines[0].status").value("SETTLED"));

        upload("VRS-006", "3000.00", csv("tracking,collected\nyal-5,3000.00"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.lines[0].status").value("ALREADY_SETTLED"));
    }

    @Test
    void thesamePayoutFileCannotBeImportedTwice() throws Exception {
        deliveredOrder("yal-6", 1);

        upload("VRS-007", "3000.00", csv("tracking,collected\nyal-6,3000.00"))
                .andExpect(status().isCreated());

        upload("VRS-007", "3000.00", csv("tracking,collected\nyal-6,3000.00"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REMITTANCE_EXISTS"));
    }

    @Test
    void pendingShowsDeliveredOrdersThatAreNotPaidYet() throws Exception {
        deliveredOrder("yal-7", 1);
        deliveredOrder("yal-8", 2);

        mvc.perform(get("/api/v1/remittances/pending").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));

        upload("VRS-008", "3000.00", csv("tracking,collected\nyal-7,3000.00"))
                .andExpect(status().isCreated());

        mvc.perform(get("/api/v1/remittances/pending").header("Authorization", token))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void unreadableRowsAreSkippedWhileTheRestIsImported() throws Exception {
        deliveredOrder("yal-9", 1);

        upload("VRS-009", "3000.00", csv("""
                tracking,collected
                yal-9,3000.00
                ,500.00
                yal-x,pas un montant"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.lineCount").value(1))
                .andExpect(jsonPath("$.matchedCount").value(1));
    }
}
