package com.westy.codmanager;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.westy.codmanager.auth.repository.UserRepository;
import com.westy.codmanager.catalog.repository.ProductRepository;
import com.westy.codmanager.customer.repository.CustomerRepository;
import com.westy.codmanager.order.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class AnalyticsIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper mapper;

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
        registry.add("app.sync.interval", () -> "PT24H");
    }

    @BeforeEach
    void setUp() throws Exception {
        orders.deleteAll();
        customers.deleteAll();
        products.deleteAll();
        users.deleteAll();

        String auth = mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"stats@example.com","password":"correct-horse",
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
                                {"size":"L","color":"Noir","sku":"TS-1-L","stockQty":500}"""))
                .andReturn().getResponse().getContentAsString();

        variantId = mapper.readTree(variant).get("id").asText();
    }

    private String order(String phone, short wilaya, String source) throws Exception {
        String response = mvc.perform(post("/api/v1/orders").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerName":"Client","phone":"%s","wilayaCode":%d,
                                 "commune":"Ville","address":"Rue","source":"%s",
                                 "deliveryType":"HOME","carrier":"YALIDINE",
                                 "items":[{"variantId":"%s","quantity":1}]}"""
                                .formatted(phone, wilaya, source, variantId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return mapper.readTree(response).get("id").asText();
    }

    private void move(String orderId, String... statuses) throws Exception {
        for (String status : statuses) {
            mvc.perform(post("/api/v1/orders/" + orderId + "/transitions")
                            .header("Authorization", token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"status":"%s","reason":"test"}""".formatted(status)))
                    .andExpect(status().isOk());
        }
    }

    private void delivered(String phone, short wilaya, String source) throws Exception {
        move(order(phone, wilaya, source),
                "CONFIRMED", "PACKED", "SHIPPED", "IN_TRANSIT", "OUT_FOR_DELIVERY", "DELIVERED");
    }

    private void returned(String phone, short wilaya, String source) throws Exception {
        move(order(phone, wilaya, source),
                "CONFIRMED", "PACKED", "SHIPPED", "IN_TRANSIT", "RETURNED");
    }

    @Test
    void anEmptyShopReportsZeroesRatherThanFailing() throws Exception {
        mvc.perform(get("/api/v1/analytics/overview").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalOrders").value(0))
                .andExpect(jsonPath("$.returnRate").value(0.0))
                .andExpect(jsonPath("$.hoursToConfirm").doesNotExist());
    }

    @Test
    void theOverviewCountsOrdersAndComputesRates() throws Exception {
        delivered("0551000001", (short) 25, "INSTAGRAM");
        delivered("0551000002", (short) 25, "INSTAGRAM");
        returned("0551000003", (short) 11, "TIKTOK");
        order("0551000004", (short) 16, "TIKTOK");

        mvc.perform(get("/api/v1/analytics/overview").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalOrders").value(4))
                .andExpect(jsonPath("$.ordersByStatus.DELIVERED").value(2))
                .andExpect(jsonPath("$.ordersByStatus.RETURNED").value(1))
                .andExpect(jsonPath("$.ordersByStatus.PENDING").value(1))
                // Two delivered against one returned out of three shipped.
                .andExpect(jsonPath("$.returnRate").value(0.3333));
    }

    @Test
    void cashOutstandingWithCarriersIsVisible() throws Exception {
        delivered("0551000005", (short) 25, "INSTAGRAM");

        mvc.perform(get("/api/v1/analytics/overview").header("Authorization", token))
                .andExpect(jsonPath("$.outstandingWithCarriers").value(3000.00))
                .andExpect(jsonPath("$.settledRevenue").value(0));
    }

    @Test
    void theBreakdownRanksWilayasByReturns() throws Exception {
        delivered("0551000006", (short) 25, "INSTAGRAM");
        returned("0551000007", (short) 11, "TIKTOK");
        returned("0551000008", (short) 11, "TIKTOK");

        mvc.perform(get("/api/v1/analytics/breakdown").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.byWilaya[0].wilayaCode").value(11))
                .andExpect(jsonPath("$.byWilaya[0].returned").value(2))
                .andExpect(jsonPath("$.byWilaya[0].returnRate").value(1.0));
    }

    @Test
    void theBreakdownComparesSalesChannels() throws Exception {
        delivered("0551000009", (short) 25, "INSTAGRAM");
        returned("0551000010", (short) 25, "TIKTOK");

        mvc.perform(get("/api/v1/analytics/breakdown").header("Authorization", token))
                .andExpect(jsonPath("$.bySource.length()").value(2))
                .andExpect(jsonPath("$.bySource[?(@.source == 'INSTAGRAM')].returnRate").value(0.0))
                .andExpect(jsonPath("$.bySource[?(@.source == 'TIKTOK')].returnRate").value(1.0));
    }

    @Test
    void topProductsCountOnlyOrdersThatActuallyArrived() throws Exception {
        delivered("0551000011", (short) 25, "INSTAGRAM");
        returned("0551000012", (short) 25, "INSTAGRAM");
        order("0551000013", (short) 25, "INSTAGRAM");

        mvc.perform(get("/api/v1/analytics/breakdown").header("Authorization", token))
                .andExpect(jsonPath("$.topProducts.length()").value(1))
                .andExpect(jsonPath("$.topProducts[0].productName").value("T-shirt"))
                .andExpect(jsonPath("$.topProducts[0].unitsSold").value(1));
    }

    @Test
    void analyticsAreClosedWithoutAToken() throws Exception {
        mvc.perform(get("/api/v1/analytics/overview")).andExpect(status().isUnauthorized());
    }

    @Test
    void anOutOfRangePeriodIsRejected() throws Exception {
        mvc.perform(get("/api/v1/analytics/overview")
                        .header("Authorization", token).param("days", "9999"))
                .andExpect(status().is4xxClientError());
    }
}
