package com.westy.codmanager;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class GeoIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void allFiftyEightWilayasAreSeeded() throws Exception {
        mvc.perform(get("/api/v1/geo/wilayas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(58))
                .andExpect(jsonPath("$[0].code").value(1))
                .andExpect(jsonPath("$[15].nameFr").value("Alger"))
                .andExpect(jsonPath("$[57].code").value(58));
    }

    @Test
    void anUnknownWilayaReturnsNotFound() throws Exception {
        mvc.perform(get("/api/v1/geo/wilayas/99/communes"))
                .andExpect(status().isNotFound());
    }

    @Test
    void deliveryFeesCoverEveryWilaya() throws Exception {
        mvc.perform(get("/api/v1/geo/delivery-fees").param("carrier", "YALIDINE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(58));
    }

    @Test
    void stopdeskIsCheaperThanHomeDelivery() throws Exception {
        mvc.perform(get("/api/v1/geo/delivery-fees")
                        .param("carrier", "YALIDINE").param("wilaya", "25"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].homePrice").value(500.00))
                .andExpect(jsonPath("$[0].stopdeskPrice").value(300.00));
    }

    @Test
    void southernWilayasCostMoreToReach() throws Exception {
        mvc.perform(get("/api/v1/geo/delivery-fees")
                        .param("carrier", "YALIDINE").param("wilaya", "11"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].homePrice").value(1400.00));
    }
}
