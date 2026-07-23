package com.inventra.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.inventra.api.auth.dto.LoginRequest;
import com.inventra.api.auth.dto.RegisterRequest;
import com.inventra.api.auth.dto.TokenResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.ConfigurableMockMvcBuilder;
import org.testcontainers.containers.MySQLContainer;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Base class for all integration tests.
 *
 * <p>Starts a single shared MySQL Testcontainer for the entire test run
 * (reuse = true), wires Spring Boot against it, and provides helper methods
 * for registering tenants and obtaining Bearer tokens.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Import(BaseIntegrationTest.SecurityMockMvcConfig.class)
public abstract class BaseIntegrationTest {

    /**
     * Bridges the Spring Security test infrastructure into the auto-configured
     * {@link MockMvc} so {@code @WithMockUser} and friends populate the
     * {@code SecurityContext} for each request. Spring Boot 4 dropped the
     * automatic application of {@code springSecurity()}, so we apply it
     * explicitly via a {@link MockMvcBuilderCustomizer} bean.
     */
    @TestConfiguration
    static class SecurityMockMvcConfig {
        @Bean
        MockMvcBuilderCustomizer securityMockMvcCustomizer() {
            return builder -> {
                if (builder instanceof ConfigurableMockMvcBuilder<?> configurable) {
                    configurable.apply(springSecurity());
                }
            };
        }
    }

    // Container and datasource/JWT wiring live in TestDatabase so a test that needs
    // the database without this class's rate-limit override can reuse them.
    static final MySQLContainer<?> MY_SQL = TestDatabase.MY_SQL;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        TestDatabase.register(registry);
        // Disable RateLimitFilter for shared-context integration tests. Its
        // singleton in-memory bucket otherwise leaks state across test classes
        // and trips 429 responses for the helper register/login calls below.
        // RateLimitFilterTest needs it enabled, so it wires TestDatabase directly
        // instead of extending this class.
        registry.add("app.security.rate-limit.enabled", () -> "false");
    }

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    /**
     * Register a new tenant and return the token pair.
     * Each call must use a unique slug and email.
     */
    protected TokenResponse registerTenant(String slug, String email, String password) throws Exception {
        RegisterRequest req = new RegisterRequest(
                "Tenant " + slug,   // tenantName
                slug,               // slug
                email,              // email
                password,           // password
                "Admin",            // firstName
                "User"              // lastName
        );

        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();

        return objectMapper.readValue(
                result.getResponse().getContentAsString(), TokenResponse.class);
    }

    /**
     * Login an existing user and return the token pair.
     */
    protected TokenResponse login(String email, String password) throws Exception {
        LoginRequest req = new LoginRequest(email, password);

        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readValue(
                result.getResponse().getContentAsString(), TokenResponse.class);
    }

    /** Convenience: Bearer header value from a token response. */
    protected String bearer(TokenResponse tokens) {
        return "Bearer " + tokens.accessToken();
    }

    /**
     * Extract the {@code id} field from a JSON response body.
     *
     * <p>Use this instead of reading a {@code Location} header — the
     * controllers return the created resource in the body, not a redirect.
     *
     * <pre>{@code
     * String categoryId = idFrom(mockMvc.perform(post("/api/v1/categories")
     *         .header("Authorization", bearer(tokens))
     *         .contentType(MediaType.APPLICATION_JSON)
     *         .content(json))
     *     .andExpect(status().isCreated())
     *     .andReturn());
     * }</pre>
     */
    protected String idFrom(org.springframework.test.web.servlet.MvcResult result) throws Exception {
        String body = result.getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asText();
    }
}
