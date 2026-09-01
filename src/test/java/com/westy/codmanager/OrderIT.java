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
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class OrderIT extends AbstractIntegrationTest {

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

    @BeforeEach
    void setUp() throws Exception {
        orders.deleteAll();
        customers.deleteAll();
        products.deleteAll();
        users.deleteAll();

        String auth = mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"seller@example.com","password":"correct-horse",
                                 "storeName":"Boutique Westy"}"""))
                .andReturn().getResponse().getContentAsString();

        token = "Bearer " + mapper.readTree(auth).get("accessToken").asText();

        String product = mvc.perform(post("/api/v1/products")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"T-shirt oversize","sku":"TS-1",
                                 "basePrice":2500.00,"costPrice":1200.00}"""))
                .andReturn().getResponse().getContentAsString();

        String productId = mapper.readTree(product).get("id").asText();

        String variant = mvc.perform(post("/api/v1/products/" + productId + "/variants")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"size":"L","color":"Noir","sku":"TS-1-L-NOIR","stockQty":10}"""))
                .andReturn().getResponse().getContentAsString();

        variantId = mapper.readTree(variant).get("id").asText();
    }

    private String orderBody(short wilaya, String deliveryType, int quantity) {
        return """
                {"customerName":"Amine B.","phone":"0551234567","wilayaCode":%d,
                 "commune":"Constantine","address":"Cité Zouaghi","source":"INSTAGRAM",
                 "deliveryType":"%s","carrier":"YALIDINE",
                 "items":[{"variantId":"%s","quantity":%d}]}"""
                .formatted(wilaya, deliveryType, variantId, quantity);
    }

    private String createOrder() throws Exception {
        String response = mvc.perform(post("/api/v1/orders")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderBody((short) 25, "HOME", 2)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return mapper.readTree(response).get("id").asText();
    }

    private void move(String orderId, String status) throws Exception {
        mvc.perform(post("/api/v1/orders/" + orderId + "/transitions")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"%s","reason":"test"}""".formatted(status)))
                .andExpect(status().isOk());
    }

    @Test
    void anOrderIsPricedFromTheDestinationWilaya() throws Exception {
        mvc.perform(post("/api/v1/orders")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderBody((short) 25, "HOME", 2)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.subtotal").value(5000.00))
                .andExpect(jsonPath("$.deliveryFee").value(500.00))
                .andExpect(jsonPath("$.total").value(5500.00))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.orderNumber").isNotEmpty());
    }

    @Test
    void stopdeskIsCheaperThanHomeDelivery() throws Exception {
        mvc.perform(post("/api/v1/orders")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderBody((short) 25, "STOPDESK", 1)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.deliveryFee").value(300.00))
                .andExpect(jsonPath("$.total").value(2800.00));
    }

    @Test
    void aNewOrderStartsWithHistoryAndValidNextStates() throws Exception {
        mvc.perform(get("/api/v1/orders/" + createOrder()).header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.history.length()").value(1))
                .andExpect(jsonPath("$.history[0].toStatus").value("PENDING"))
                .andExpect(jsonPath("$.nextStates").isNotEmpty());
    }

    @Test
    void anIllegalTransitionIsRejected() throws Exception {
        mvc.perform(post("/api/v1/orders/" + createOrder() + "/transitions")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"DELIVERED","reason":"skipping ahead"}"""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ILLEGAL_TRANSITION"));
    }

    @Test
    void stockIsReservedOnConfirmationNotOnCreation() throws Exception {
        String orderId = createOrder();

        mvc.perform(get("/api/v1/products").header("Authorization", token))
                .andExpect(jsonPath("$.content[0].variants[0].stockQty").value(10));

        move(orderId, "CONFIRMED");

        mvc.perform(get("/api/v1/products").header("Authorization", token))
                .andExpect(jsonPath("$.content[0].variants[0].stockQty").value(8));
    }

    @Test
    void cancellingAConfirmedOrderPutsTheStockBack() throws Exception {
        String orderId = createOrder();
        move(orderId, "CONFIRMED");
        move(orderId, "CANCELLED");

        mvc.perform(get("/api/v1/products").header("Authorization", token))
                .andExpect(jsonPath("$.content[0].variants[0].stockQty").value(10));
    }

    @Test
    void theFullLifecycleRecordsEveryStep() throws Exception {
        String orderId = createOrder();

        move(orderId, "CONFIRMED");
        move(orderId, "PACKED");
        move(orderId, "SHIPPED");
        move(orderId, "IN_TRANSIT");
        move(orderId, "OUT_FOR_DELIVERY");
        move(orderId, "DELIVERED");
        move(orderId, "SETTLED");

        mvc.perform(get("/api/v1/orders/" + orderId).header("Authorization", token))
                .andExpect(jsonPath("$.status").value("SETTLED"))
                .andExpect(jsonPath("$.history.length()").value(8))
                .andExpect(jsonPath("$.confirmedAt").isNotEmpty())
                .andExpect(jsonPath("$.deliveredAt").isNotEmpty())
                .andExpect(jsonPath("$.nextStates").isEmpty());
    }

    @Test
    void anInvalidPhoneNumberIsRejected() throws Exception {
        String body = """
                {"customerName":"Amine B.","phone":"12345","wilayaCode":25,
                 "commune":"Constantine","source":"INSTAGRAM","deliveryType":"HOME",
                 "carrier":"YALIDINE","items":[{"variantId":"%s","quantity":1}]}"""
                .formatted(variantId);

        mvc.perform(post("/api/v1/orders")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.phone").isNotEmpty());
    }

    @Test
    void confirmingMoreThanTheAvailableStockFails() throws Exception {
        String response = mvc.perform(post("/api/v1/orders")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderBody((short) 25, "HOME", 50)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String orderId = mapper.readTree(response).get("id").asText();

        mvc.perform(post("/api/v1/orders/" + orderId + "/transitions")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"CONFIRMED","reason":"too many"}"""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"));
    }

    @Test
    void theItemKeepsThePriceItWasOrderedAt() throws Exception {
        String orderId = createOrder();

        mvc.perform(get("/api/v1/orders/" + orderId).header("Authorization", token))
                .andExpect(jsonPath("$.items[0].unitPrice").value(2500.00))
                .andExpect(jsonPath("$.items[0].productName").value("T-shirt oversize"))
                .andExpect(jsonPath("$.items[0].lineTotal").value(5000.00));
    }
}
