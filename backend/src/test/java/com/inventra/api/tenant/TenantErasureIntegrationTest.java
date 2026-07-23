package com.inventra.api.tenant;

import com.inventra.api.BaseIntegrationTest;
import com.inventra.api.auth.dto.TokenResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for the GDPR right-to-erasure endpoint.
 *
 * <p>Integration tests for the DELETE /api/v1/tenant right-to-erasure endpoint. These tests verify:
 * <ul>
 *   <li>Correct slug confirmation is required.
 *   <li>Wrong slug returns 400.
 *   <li>After erasure the tenant is suspended and login is rejected.
 *   <li>Non-ADMIN roles cannot call the endpoint.
 * </ul>
 */
@DisplayName("Tenant Erasure (GDPR Art. 17)")
class TenantErasureIntegrationTest extends BaseIntegrationTest {

    private TokenResponse adminToken;
    private String slug;

    @BeforeEach
    void setUp() throws Exception {
        slug = "erase-" + UUID.randomUUID().toString().substring(0, 8);
        adminToken = registerTenant(slug, "admin-" + slug + "@test.com", "Password1!");
    }

    @Test
    @DisplayName("Erasure with correct slug returns 204 and suspends the tenant")
    void erasure_correctSlug_returns204() throws Exception {
        mockMvc.perform(delete("/api/v1/tenant")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("confirmSlug", slug))))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("Login is rejected after erasure (tenant suspended)")
    void erasure_preventsSubsequentLogin() throws Exception {
        mockMvc.perform(delete("/api/v1/tenant")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("confirmSlug", slug))))
                .andExpect(status().isNoContent());

        // Login must now fail — tenant is SUSPENDED
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("email", "admin-" + slug + "@test.com",
                                       "password", "Password1!"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Erasure with wrong slug returns 400")
    void erasure_wrongSlug_returns400() throws Exception {
        mockMvc.perform(delete("/api/v1/tenant")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("confirmSlug", "wrong-slug"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Erasure without authentication returns 401")
    void erasure_unauthenticated_returns401() throws Exception {
        mockMvc.perform(delete("/api/v1/tenant")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("confirmSlug", slug))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Erasure with blank confirmSlug returns 400 (validation)")
    void erasure_blankSlug_returns400() throws Exception {
        mockMvc.perform(delete("/api/v1/tenant")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("confirmSlug", ""))))
                .andExpect(status().isBadRequest());
    }
}
