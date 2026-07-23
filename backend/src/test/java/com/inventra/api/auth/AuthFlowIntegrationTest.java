package com.inventra.api.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.inventra.api.BaseIntegrationTest;
import com.inventra.api.auth.dto.TokenResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Auth flow integration tests.
 *
 * <p>Covers: register, login, protected-resource access,
 * refresh-token rotation, refresh-token replay (must 401),
 * and logout (revokes the refresh token).
 */
@DisplayName("Auth Flow")
class AuthFlowIntegrationTest extends BaseIntegrationTest {

    @Test
    @DisplayName("Register returns access + refresh token")
    void register_returnsTokenPair() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "tenantName", "Test Corp",
                                "slug", "test-corp-" + suffix,
                                "email", "admin-" + suffix + "@test.com",
                                "password", "Password1!",
                                "firstName", "Admin",
                                "lastName", "User"
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andReturn();

        TokenResponse tokens = objectMapper.readValue(
                result.getResponse().getContentAsString(), TokenResponse.class);
        assertThat(tokens.accessToken()).isNotBlank();
        assertThat(tokens.refreshToken()).isNotBlank();
    }

    @Test
    @DisplayName("Duplicate slug on register returns 409")
    void register_duplicateSlug_returns409() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String slug = "dup-slug-" + suffix;

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "tenantName", "Corp A",
                                "slug", slug,
                                "email", "a-" + suffix + "@test.com",
                                "password", "Password1!"
                        ))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "tenantName", "Corp B",
                                "slug", slug,
                                "email", "b-" + suffix + "@test.com",
                                "password", "Password1!"
                        ))))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Login with valid credentials returns token pair")
    void login_validCredentials_returnsTokens() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String email = "login-" + suffix + "@test.com";
        registerTenant("login-slug-" + suffix, email, "Password1!");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email,
                                "password", "Password1!"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty());
    }

    @Test
    @DisplayName("Login with wrong password returns 401")
    void login_wrongPassword_returns401() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String email = "badpw-" + suffix + "@test.com";
        registerTenant("badpw-" + suffix, email, "Password1!");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email,
                                "password", "WrongPassword!"
                        ))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Login with unknown email returns 401")
    void login_unknownEmail_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "nobody@nowhere.example",
                                "password", "Password1!"
                        ))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Access token grants access to protected endpoints")
    void accessToken_grantsAccessToProtectedEndpoint() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        TokenResponse tokens = registerTenant("prot-" + suffix, "prot-" + suffix + "@test.com", "Password1!");

        mockMvc.perform(get("/api/v1/products")
                        .header("Authorization", bearer(tokens)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Missing token returns 401 on protected endpoint")
    void noToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/products"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Refresh returns a new token pair")
    void refresh_returnsNewTokenPair() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        TokenResponse original = registerTenant("ref-" + suffix, "ref-" + suffix + "@test.com", "Password1!");

        // JWT 'iat' is second-precision. If the refresh request hits within the
        // same second as registration, the new access token can byte-for-byte
        // match the original. Sleep just past 1 s so the issued-at differs.
        Thread.sleep(1_100);

        MvcResult result = mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("refreshToken", original.refreshToken()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andReturn();

        TokenResponse refreshed = objectMapper.readValue(
                result.getResponse().getContentAsString(), TokenResponse.class);

        // New tokens must differ from originals
        assertThat(refreshed.accessToken()).isNotEqualTo(original.accessToken());
        assertThat(refreshed.refreshToken()).isNotEqualTo(original.refreshToken());
    }

    @Test
    @DisplayName("Refresh token replay: second use of the same token returns 401")
    void refreshToken_replay_returns401() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        TokenResponse original = registerTenant("replay-" + suffix, "replay-" + suffix + "@test.com", "Password1!");

        String refreshPayload = objectMapper.writeValueAsString(
                Map.of("refreshToken", original.refreshToken()));

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshPayload))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshPayload))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Completely invalid refresh token returns 401")
    void refreshToken_invalid_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("refreshToken", UUID.randomUUID().toString()))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Logout returns 204 and invalidates the refresh token")
    void logout_revokesRefreshToken() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        TokenResponse tokens = registerTenant("logout-" + suffix, "logout-" + suffix + "@test.com", "Password1!");

        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("refreshToken", tokens.refreshToken()))))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("refreshToken", tokens.refreshToken()))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Access token from before logout still works until it expires")
    void logout_doesNotInvalidateAccessToken() throws Exception {
        // Access tokens are stateless JWTs — they cannot be revoked server-side.
        // This test documents that behaviour: the access token remains usable
        // until it expires naturally (15 minutes in default config).
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        TokenResponse tokens = registerTenant("at-" + suffix, "at-" + suffix + "@test.com", "Password1!");

        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("refreshToken", tokens.refreshToken()))))
                .andExpect(status().isNoContent());

        // Access token still works (intended — short-lived JWT, no revocation list)
        mockMvc.perform(get("/api/v1/products")
                        .header("Authorization", bearer(tokens)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Register with password shorter than 8 chars returns 400")
    void register_shortPassword_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "tenantName", "Corp",
                                "slug", "short-pw-" + UUID.randomUUID().toString().substring(0, 8),
                                "email", "x@test.com",
                                "password", "abc"
                        ))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Register with invalid slug characters returns 400")
    void register_invalidSlug_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "tenantName", "Corp",
                                "slug", "UPPERCASE_SLUG",
                                "email", "x@test.com",
                                "password", "Password1!"
                        ))))
                .andExpect(status().isBadRequest());
    }
}
