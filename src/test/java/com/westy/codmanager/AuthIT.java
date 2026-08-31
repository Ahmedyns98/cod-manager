package com.westy.codmanager;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.westy.codmanager.auth.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class AuthIT extends AbstractIntegrationTest {

    private static final String REGISTER = """
            {"email":"%s","password":"%s","storeName":"Boutique Westy"}""";

    private static final String LOGIN = """
            {"email":"%s","password":"%s"}""";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UserRepository users;

    @Autowired
    private ObjectMapper mapper;

    @BeforeEach
    void clean() {
        users.deleteAll();
    }

    @Test
    void registerCreatesAnAccountAndReturnsAToken() throws Exception {
        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REGISTER.formatted("westy@example.com", "correct-horse")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"));

        assertThat(users.existsByEmail("westy@example.com")).isTrue();
    }

    @Test
    void passwordIsNeverStoredInPlainText() throws Exception {
        mvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(REGISTER.formatted("hash@example.com", "correct-horse")));

        String stored = users.findByEmail("hash@example.com").orElseThrow().getPasswordHash();

        assertThat(stored).isNotEqualTo("correct-horse").startsWith("$2");
    }

    @Test
    void theSameEmailCannotRegisterTwice() throws Exception {
        String body = REGISTER.formatted("dup@example.com", "correct-horse");

        mvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON).content(body));

        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_TAKEN"));
    }

    @Test
    void shortPasswordsAreRejected() throws Exception {
        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REGISTER.formatted("short@example.com", "abc")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.password").isNotEmpty());
    }

    @Test
    void loginWithTheWrongPasswordIsRejected() throws Exception {
        mvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(REGISTER.formatted("login@example.com", "correct-horse")));

        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(LOGIN.formatted("login@example.com", "wrong-password")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void meIsRejectedWithoutATokenAndReturnsTheAccountWithOne() throws Exception {
        mvc.perform(get("/api/v1/auth/me")).andExpect(status().isUnauthorized());

        String response = mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REGISTER.formatted("me@example.com", "correct-horse")))
                .andReturn().getResponse().getContentAsString();

        JsonNode json = mapper.readTree(response);
        String token = json.get("accessToken").asText();

        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("me@example.com"))
                .andExpect(jsonPath("$.role").value("OWNER"));
    }

    @Test
    void aTamperedTokenIsRejected() throws Exception {
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer not.a.real.token"))
                .andExpect(status().isUnauthorized());
    }
}
