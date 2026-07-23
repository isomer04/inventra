package com.inventra.api.response;

import com.inventra.api.BaseIntegrationTest;
import com.inventra.api.auth.dto.TokenResponse;
import com.inventra.api.category.dto.CreateCategoryRequest;
import com.inventra.api.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Response Shape and Status Codes Tests
 *
 * <p>Tests that endpoints return correct DTO structure and HTTP status codes.
 *
 * <p>Extends {@link BaseIntegrationTest} for the shared Testcontainers MySQL
 * and rate-limit-disabled context.
 *
 * <p>Rewritten to match the actual API contract
 * (no {@code Location} header on POST, {@code violations[]} not {@code errors},
 * singular table names, etc.).
 */
@DisplayName("Response Shape and Status Codes")
class ResponseShapeTest extends BaseIntegrationTest {

    @Nested
    @DisplayName("Success Responses")
    @WithMockUser(roles = "MANAGER")
    class SuccessResponses {

        private TokenResponse tokens;

        @BeforeEach
        void setUp() throws Exception {
            String uid = String.valueOf(System.currentTimeMillis());
            tokens = registerTenant("resp-" + uid, "resp-" + uid + "@test.com", "Password1!");
        }

        @Test
        @DisplayName("GET /api/v1/categories returns 200 with array")
        void getCategories_thenReturns200WithList() throws Exception {
            mockMvc.perform(get("/api/v1/categories")
                    .header("Authorization", bearer(tokens)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").isArray());
        }

        @Test
        @DisplayName("POST /api/v1/categories returns 201 with CategoryResponse body")
        void createCategory_thenReturns201WithBody() throws Exception {
            // parentId = null (second param) — not a description
            CreateCategoryRequest request = new CreateCategoryRequest("Test Category", null);

            mockMvc.perform(post("/api/v1/categories")
                    .header("Authorization", bearer(tokens))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.name").value("Test Category"));
        }
    }

    @Nested
    @DisplayName("Error Responses")
    @WithMockUser(roles = "MANAGER")
    class ErrorResponses {

        private static final String FAKE_TENANT = "test-tenant-response-shape";

        @BeforeEach
        void setTenant() {
            TenantContext.setTenantId(FAKE_TENANT);
        }

        @AfterEach
        void clearTenant() {
            TenantContext.clear();
        }

        @Test
        @DisplayName("GET /api/v1/categories/{nonexistent} returns 404 with ApiError")
        void getCategory_whenNotFound_thenReturns404WithError() throws Exception {
            mockMvc.perform(get("/api/v1/categories/nonexistent-id"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.timestamp").exists());
        }

        @Test
        @DisplayName("POST /api/v1/categories with blank name returns 400 with violations")
        void createCategory_whenInvalid_thenReturns400WithError() throws Exception {
            CreateCategoryRequest request = new CreateCategoryRequest("", null);

            mockMvc.perform(post("/api/v1/categories")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.violations").isArray());
        }

        @Test
        @DisplayName("PUT /api/v1/categories/{nonexistent} returns 404 with ApiError")
        void updateCategory_whenNotFound_thenReturns404WithError() throws Exception {
            CreateCategoryRequest request = new CreateCategoryRequest("Updated", null);

            mockMvc.perform(put("/api/v1/categories/nonexistent-id")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").exists());
        }
    }

    @Nested
    @DisplayName("Report Controller Responses")
    class ReportResponses {

        private TokenResponse tokens;

        @BeforeEach
        void setUp() throws Exception {
            String uid = String.valueOf(System.currentTimeMillis());
            tokens = registerTenant("rpt-" + uid, "rpt-" + uid + "@test.com", "Password1!");
        }

        @Test
        @DisplayName("GET /api/v1/reports/inventory-summary returns 200 with InventorySummaryResponse")
        void getInventoryReport_thenReturns200WithList() throws Exception {
            mockMvc.perform(get("/api/v1/reports/inventory-summary")
                    .header("Authorization", bearer(tokens)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));
        }

        @Test
        @DisplayName("GET /api/v1/reports/order-summary returns 200 with OrderSummaryResponse")
        void getSalesReport_thenReturns200WithObject() throws Exception {
            mockMvc.perform(get("/api/v1/reports/order-summary")
                    .header("Authorization", bearer(tokens))
                    .param("startDate", "2026-01-01")
                    .param("endDate", "2026-12-31"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));
        }

        @Test
        @DisplayName("GET /api/v1/reports/order-summary with end before start returns 400")
        void getSalesReport_whenInvalidDateRange_thenReturns400() throws Exception {
            mockMvc.perform(get("/api/v1/reports/order-summary")
                    .header("Authorization", bearer(tokens))
                    .param("startDate", "2026-12-31")
                    .param("endDate", "2026-01-01"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").exists());
        }
    }
}
