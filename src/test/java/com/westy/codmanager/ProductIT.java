package com.westy.codmanager;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.westy.codmanager.auth.repository.UserRepository;
import com.westy.codmanager.catalog.repository.ProductRepository;
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
class ProductIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper mapper;

    @Autowired
    private UserRepository users;

    @Autowired
    private ProductRepository products;

    @BeforeEach
    void clean() {
        products.deleteAll();
        users.deleteAll();
    }

    private String tokenFor(String email) throws Exception {
        String body = """
                {"email":"%s","password":"correct-horse","storeName":"Boutique"}"""
                .formatted(email);

        String response = mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn().getResponse().getContentAsString();

        return "Bearer " + mapper.readTree(response).get("accessToken").asText();
    }

    private String createProduct(String token, String sku) throws Exception {
        String body = """
                {"name":"T-shirt oversize","sku":"%s","basePrice":2500.00,"costPrice":1200.00}"""
                .formatted(sku);

        String response = mvc.perform(post("/api/v1/products")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return mapper.readTree(response).get("id").asText();
    }

    @Test
    void creatingAProductComputesTheUnitMargin() throws Exception {
        String token = tokenFor("margin@example.com");

        mvc.perform(post("/api/v1/products")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Hoodie","sku":"HOOD-1","basePrice":4000.00,"costPrice":1800.00}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.unitMargin").value(2200.00));
    }

    @Test
    void theCatalogIsClosedWithoutAToken() throws Exception {
        mvc.perform(get("/api/v1/products")).andExpect(status().isUnauthorized());
    }

    @Test
    void theSameSellerCannotReuseASku() throws Exception {
        String token = tokenFor("dup@example.com");
        createProduct(token, "TSHIRT-1");

        mvc.perform(post("/api/v1/products")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Another","sku":"TSHIRT-1","basePrice":100.00,"costPrice":10.00}"""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SKU_TAKEN"));
    }

    @Test
    void oneSellerCannotSeeAnotherSellersProduct() throws Exception {
        String alice = tokenFor("alice@example.com");
        String productId = createProduct(alice, "ALICE-1");

        String bob = tokenFor("bob@example.com");

        mvc.perform(get("/api/v1/products/" + productId).header("Authorization", bob))
                .andExpect(status().isNotFound());
    }

    @Test
    void variantsAreReturnedWithTheirProduct() throws Exception {
        String token = tokenFor("variant@example.com");
        String productId = createProduct(token, "VAR-1");

        mvc.perform(post("/api/v1/products/" + productId + "/variants")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"size":"L","color":"Noir","sku":"VAR-1-L-NOIR","stockQty":25}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.stockQty").value(25));

        mvc.perform(get("/api/v1/products/" + productId).header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.variants.length()").value(1))
                .andExpect(jsonPath("$.variants[0].sku").value("VAR-1-L-NOIR"));
    }

    @Test
    void negativePricesAreRejected() throws Exception {
        String token = tokenFor("neg@example.com");

        mvc.perform(post("/api/v1/products")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Bad","sku":"BAD-1","basePrice":-5.00,"costPrice":0.00}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.basePrice").isNotEmpty());
    }
}
