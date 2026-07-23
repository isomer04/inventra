package com.inventra.api.tenancy;

import com.fasterxml.jackson.databind.JsonNode;
import com.inventra.api.BaseIntegrationTest;
import com.inventra.api.auth.dto.TokenResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tenant isolation integration test suite.
 *
 * <p>Proves that data created under Tenant A is never visible to Tenant B,
 * and that Tenant B cannot read, modify, or delete Tenant A's resources
 * across every API resource: products, categories, inventory, customers, and orders.
 *
 * <p>Setup (once per class):
 *   - Tenant A is registered with its own admin token.
 *   - Tenant B is registered with its own admin token.
 *   - A product, category, and customer are created under Tenant A.
 *   - Tenant B tries to access every one of those resources.
 */
@DisplayName("Tenant Isolation")
class TenantIsolationIntegrationTest extends BaseIntegrationTest {

    private TokenResponse tokenA;
    private TokenResponse tokenB;

    private String categoryIdA;
    private String productIdA;
    private String customerIdA;

    private final String suffix = UUID.randomUUID().toString().substring(0, 8);

    @BeforeEach
    void setUp() throws Exception {
        tokenA = registerTenant("tenant-alpha-" + suffix,
                "admin-alpha-" + suffix + "@example.com", "Password1!");
        tokenB = registerTenant("tenant-beta-" + suffix,
                "admin-beta-" + suffix + "@example.com", "Password1!");

        categoryIdA = createCategory(tokenA, "Electronics-" + suffix);
        productIdA = createProduct(tokenA, "SKU-" + suffix, "Laptop-" + suffix, categoryIdA);
        customerIdA = createCustomer(tokenA, "Alice Corp " + suffix);
    }

    @Nested
    @DisplayName("Categories")
    class CategoryIsolation {

        @Test
        @DisplayName("Tenant B cannot GET Tenant A's category")
        void tenantB_cannotGetTenantA_category() throws Exception {
            mockMvc.perform(get("/api/v1/categories/" + categoryIdA)
                            .header("Authorization", bearer(tokenB)))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("Tenant B cannot UPDATE Tenant A's category")
        void tenantB_cannotUpdateTenantA_category() throws Exception {
            mockMvc.perform(put("/api/v1/categories/" + categoryIdA)
                            .header("Authorization", bearer(tokenB))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"name": "Hijacked Category"}
                                    """))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("Tenant B cannot DELETE Tenant A's category")
        void tenantB_cannotDeleteTenantA_category() throws Exception {
            mockMvc.perform(delete("/api/v1/categories/" + categoryIdA)
                            .header("Authorization", bearer(tokenB)))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("Tenant B list returns only its own categories")
        void tenantB_list_returnsOnlyOwnCategories() throws Exception {
            String catB = createCategory(tokenB, "Furniture-" + suffix);

            MvcResult result = mockMvc.perform(get("/api/v1/categories")
                            .header("Authorization", bearer(tokenB)))
                    .andExpect(status().isOk())
                    .andReturn();

            String body = result.getResponse().getContentAsString();
            assertThat(body).contains(catB);
            assertThat(body).doesNotContain(categoryIdA);
        }
    }

    @Nested
    @DisplayName("Products")
    class ProductIsolation {

        @Test
        @DisplayName("Tenant B cannot GET Tenant A's product")
        void tenantB_cannotGetTenantA_product() throws Exception {
            mockMvc.perform(get("/api/v1/products/" + productIdA)
                            .header("Authorization", bearer(tokenB)))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("Tenant B cannot UPDATE Tenant A's product")
        void tenantB_cannotUpdateTenantA_product() throws Exception {
            mockMvc.perform(put("/api/v1/products/" + productIdA)
                            .header("Authorization", bearer(tokenB))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"name": "Hijacked Product", "unitPrice": 0.01}
                                    """))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("Tenant B cannot DELETE Tenant A's product")
        void tenantB_cannotDeleteTenantA_product() throws Exception {
            mockMvc.perform(delete("/api/v1/products/" + productIdA)
                            .header("Authorization", bearer(tokenB)))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("Tenant B product list does not contain Tenant A's product")
        void tenantB_list_doesNotContainTenantA_product() throws Exception {
            mockMvc.perform(get("/api/v1/products")
                            .header("Authorization", bearer(tokenB)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[?(@.id == '" + productIdA + "')]").doesNotExist());
        }

        @Test
        @DisplayName("Tenant A's product is visible to Tenant A")
        void tenantA_canGetOwnProduct() throws Exception {
            mockMvc.perform(get("/api/v1/products/" + productIdA)
                            .header("Authorization", bearer(tokenA)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(productIdA));
        }
    }

    @Nested
    @DisplayName("Inventory")
    class InventoryIsolation {

        @Test
        @DisplayName("Tenant B cannot GET Tenant A's inventory item")
        void tenantB_cannotGetTenantA_inventoryItem() throws Exception {
            mockMvc.perform(get("/api/v1/inventory/" + productIdA)
                            .header("Authorization", bearer(tokenB)))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("Tenant B cannot receive stock into Tenant A's product")
        void tenantB_cannotReceiveStockForTenantA_product() throws Exception {
            mockMvc.perform(put("/api/v1/inventory/" + productIdA + "/receive")
                            .header("Authorization", bearer(tokenB))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"quantity": 100}
                                    """))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("Tenant B cannot adjust stock of Tenant A's product")
        void tenantB_cannotAdjustStockForTenantA_product() throws Exception {
            mockMvc.perform(put("/api/v1/inventory/" + productIdA + "/adjust")
                            .header("Authorization", bearer(tokenB))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"quantity": 50, "notes": "Adjustment"}
                                    """))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("Tenant B inventory list does not contain Tenant A's items")
        void tenantB_inventoryList_doesNotContainTenantA_items() throws Exception {
            mockMvc.perform(get("/api/v1/inventory")
                            .header("Authorization", bearer(tokenB)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[?(@.productId == '" + productIdA + "')]").doesNotExist());
        }
    }

    @Nested
    @DisplayName("Customers")
    class CustomerIsolation {

        @Test
        @DisplayName("Tenant B cannot GET Tenant A's customer")
        void tenantB_cannotGetTenantA_customer() throws Exception {
            mockMvc.perform(get("/api/v1/customers/" + customerIdA)
                            .header("Authorization", bearer(tokenB)))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("Tenant B cannot UPDATE Tenant A's customer")
        void tenantB_cannotUpdateTenantA_customer() throws Exception {
            mockMvc.perform(put("/api/v1/customers/" + customerIdA)
                            .header("Authorization", bearer(tokenB))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"name": "Hijacked Customer"}
                                    """))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("Tenant B cannot DELETE Tenant A's customer")
        void tenantB_cannotDeleteTenantA_customer() throws Exception {
            mockMvc.perform(delete("/api/v1/customers/" + customerIdA)
                            .header("Authorization", bearer(tokenB)))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("Tenant B customer list does not contain Tenant A's customer")
        void tenantB_list_doesNotContainTenantA_customer() throws Exception {
            mockMvc.perform(get("/api/v1/customers")
                            .header("Authorization", bearer(tokenB)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[?(@.id == '" + customerIdA + "')]").doesNotExist());
        }

        @Test
        @DisplayName("Tenant A's customer is visible to Tenant A")
        void tenantA_canGetOwnCustomer() throws Exception {
            mockMvc.perform(get("/api/v1/customers/" + customerIdA)
                            .header("Authorization", bearer(tokenA)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(customerIdA));
        }
    }

    @Nested
    @DisplayName("Orders")
    class OrderIsolation {

        private String orderIdA;

        @BeforeEach
        void createOrderUnderTenantA() throws Exception {
            // First give Tenant A's product some stock so the order can be submitted later
            mockMvc.perform(put("/api/v1/inventory/" + productIdA + "/receive")
                            .header("Authorization", bearer(tokenA))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"quantity": 100}
                                    """))
                    .andExpect(status().isOk());

            // Create a DRAFT order under Tenant A
            String body = objectMapper.writeValueAsString(Map.of(
                    "customerId", customerIdA,
                    "items", java.util.List.of(Map.of("productId", productIdA, "quantity", 1))
            ));

            MvcResult result = mockMvc.perform(post("/api/v1/orders")
                            .header("Authorization", bearer(tokenA))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isCreated())
                    .andReturn();

            JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
            orderIdA = json.get("id").asText();
        }

        @Test
        @DisplayName("Tenant B cannot GET Tenant A's order")
        void tenantB_cannotGetTenantA_order() throws Exception {
            mockMvc.perform(get("/api/v1/orders/" + orderIdA)
                            .header("Authorization", bearer(tokenB)))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("Tenant B cannot UPDATE Tenant A's order")
        void tenantB_cannotUpdateTenantA_order() throws Exception {
            mockMvc.perform(put("/api/v1/orders/" + orderIdA)
                            .header("Authorization", bearer(tokenB))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"notes": "Hijacked notes"}
                                    """))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("Tenant B cannot DELETE Tenant A's order")
        void tenantB_cannotDeleteTenantA_order() throws Exception {
            mockMvc.perform(delete("/api/v1/orders/" + orderIdA)
                            .header("Authorization", bearer(tokenB)))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("Tenant B cannot submit Tenant A's order")
        void tenantB_cannotSubmitTenantA_order() throws Exception {
            mockMvc.perform(post("/api/v1/orders/" + orderIdA + "/submit")
                            .header("Authorization", bearer(tokenB))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("Tenant B order list does not contain Tenant A's orders")
        void tenantB_list_doesNotContainTenantA_orders() throws Exception {
            mockMvc.perform(get("/api/v1/orders")
                            .header("Authorization", bearer(tokenB)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[?(@.id == '" + orderIdA + "')]").doesNotExist());
        }

        @Test
        @DisplayName("Tenant B cannot view Tenant A's order status history")
        void tenantB_cannotGetTenantA_orderHistory() throws Exception {
            mockMvc.perform(get("/api/v1/orders/" + orderIdA + "/history")
                            .header("Authorization", bearer(tokenB)))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("Tenant A can view its own order")
        void tenantA_canGetOwnOrder() throws Exception {
            mockMvc.perform(get("/api/v1/orders/" + orderIdA)
                            .header("Authorization", bearer(tokenA)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(orderIdA));
        }
    }

    @Nested
    @DisplayName("Unauthenticated access is rejected")
    class UnauthenticatedAccess {

        @Test
        @DisplayName("No token -> 401 on products")
        void noToken_products_returns401() throws Exception {
            mockMvc.perform(get("/api/v1/products"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("No token -> 401 on orders")
        void noToken_orders_returns401() throws Exception {
            mockMvc.perform(get("/api/v1/orders"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Malformed token -> 401")
        void malformedToken_returns401() throws Exception {
            mockMvc.perform(get("/api/v1/products")
                            .header("Authorization", "Bearer not.a.real.jwt"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Token from Tenant A used to access non-existent resource returns 404, not 500")
        void wrongIdFormat_returns404NotServerError() throws Exception {
            mockMvc.perform(get("/api/v1/products/00000000-0000-0000-0000-000000000000")
                            .header("Authorization", bearer(tokenA)))
                    .andExpect(status().isNotFound());
        }
    }

    private String createCategory(TokenResponse token, String name) throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("name", name));

        MvcResult result = mockMvc.perform(post("/api/v1/categories")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        return json.get("id").asText();
    }

    private String createProduct(TokenResponse token, String sku, String name, String categoryId)
            throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "sku", sku,
                "name", name,
                "categoryId", categoryId,
                "unitPrice", new BigDecimal("9.99")
        ));

        MvcResult result = mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        return json.get("id").asText();
    }

    private String createCustomer(TokenResponse token, String name) throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "name", name,
                "email", name.toLowerCase().replace(" ", "") + "@example.com"
        ));

        MvcResult result = mockMvc.perform(post("/api/v1/customers")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        return json.get("id").asText();
    }
}
