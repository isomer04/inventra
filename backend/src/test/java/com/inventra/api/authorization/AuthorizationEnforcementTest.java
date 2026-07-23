package com.inventra.api.authorization;

import com.inventra.api.BaseIntegrationTest;
import com.inventra.api.category.dto.CreateCategoryRequest;
import com.inventra.api.product.dto.CreateProductRequest;
import com.inventra.api.tenant.TenantContext;
import com.inventra.api.tenant.dto.DeleteTenantRequest;
import com.inventra.api.user.dto.CreateUserRequest;
import com.inventra.api.entity.UserRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;

import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Authorization Enforcement Tests
 *
 * Tests that @PreAuthorize annotations correctly enforce role requirements.
 * Verifies 401 (unauthenticated) and 403 (forbidden) responses.
 *
 * <p>Extends {@link BaseIntegrationTest} for the shared Testcontainers MySQL
 * and rate-limit-disabled context.
 *
 * <p>Uses {@code @WithMockUser} for authentication. Because {@code JwtAuthFilter}
 * is bypassed, {@link TenantContext} is set manually in {@code @BeforeEach}.
 */
@DisplayName("Authorization Enforcement")
class AuthorizationEnforcementTest extends BaseIntegrationTest {

    private static final String TEST_TENANT_ID = "test-tenant-auth-enforcement";

    @BeforeEach
    void setTenantContext() {
        TenantContext.setTenantId(TEST_TENANT_ID);
    }

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    @Nested
    @DisplayName("Admin-Only Endpoints")
    class AdminOnlyEndpoints {

        @Test
        @WithMockUser(roles = "MANAGER")
        @DisplayName("POST /api/v1/users without ADMIN role returns 403")
        void createUser_withoutAdminRole_thenReturns403() throws Exception {
            CreateUserRequest request = new CreateUserRequest(
                "user@example.com",
                "password123",
                "John",
                "Doe",
                UserRole.VIEWER
            );

            mockMvc.perform(post("/api/v1/users")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser(roles = "VIEWER")
        @DisplayName("DELETE /api/v1/users/{id} without ADMIN role returns 403")
        void deleteUser_withoutAdminRole_thenReturns403() throws Exception {
            mockMvc.perform(delete("/api/v1/users/test-user-id"))
                .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser(roles = "MANAGER")
        @DisplayName("DELETE /api/v1/tenant without ADMIN role returns 403")
        void eraseTenant_withoutAdminRole_thenReturns403() throws Exception {
            DeleteTenantRequest request = new DeleteTenantRequest("test-tenant");

            mockMvc.perform(delete("/api/v1/tenant")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("Manager-Only Endpoints")
    class ManagerOnlyEndpoints {

        @Test
        @WithMockUser(roles = "VIEWER")
        @DisplayName("POST /api/v1/products without MANAGER role returns 403")
        void createProduct_withoutManagerRole_thenReturns403() throws Exception {
            CreateProductRequest request = new CreateProductRequest(
                "SKU-001",
                "Product Name",
                null,
                null,
                BigDecimal.TEN,
                null
            );

            mockMvc.perform(post("/api/v1/products")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser(roles = "WAREHOUSE_STAFF")
        @DisplayName("DELETE /api/v1/products/{id} without MANAGER role returns 403")
        void deleteProduct_withoutManagerRole_thenReturns403() throws Exception {
            mockMvc.perform(delete("/api/v1/products/test-product-id"))
                .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser(roles = "VIEWER")
        @DisplayName("POST /api/v1/categories without MANAGER role returns 403")
        void createCategory_withoutManagerRole_thenReturns403() throws Exception {
            CreateCategoryRequest request = new CreateCategoryRequest("Category Name", null);

            mockMvc.perform(post("/api/v1/categories")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser(roles = "WAREHOUSE_STAFF")
        @DisplayName("DELETE /api/v1/categories/{id} without MANAGER role returns 403")
        void deleteCategory_withoutManagerRole_thenReturns403() throws Exception {
            mockMvc.perform(delete("/api/v1/categories/test-category-id"))
                .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("Unauthenticated Access")
    class UnauthenticatedAccess {

        @Test
        @DisplayName("GET /api/v1/products without token returns 401")
        void getProducts_withoutToken_thenReturns401() throws Exception {
            mockMvc.perform(get("/api/v1/products"))
                .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("GET /api/v1/orders without token returns 401")
        void getOrders_withoutToken_thenReturns401() throws Exception {
            mockMvc.perform(get("/api/v1/orders"))
                .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("GET /api/v1/customers without token returns 401")
        void getCustomers_withoutToken_thenReturns401() throws Exception {
            mockMvc.perform(get("/api/v1/customers"))
                .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("GET /api/v1/inventory without token returns 401")
        void getInventory_withoutToken_thenReturns401() throws Exception {
            mockMvc.perform(get("/api/v1/inventory"))
                .andExpect(status().isUnauthorized());
        }
    }
}
