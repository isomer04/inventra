package com.inventra.api.edgecase;

import com.inventra.api.BaseIntegrationTest;
import com.inventra.api.product.dto.CreateProductRequest;
import com.inventra.api.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;

import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP edge cases with assertions on the actual contract. Concurrency behavior is
 * covered by the dedicated optimistic-locking and inventory concurrency suites.
 */
@WithMockUser(roles = "MANAGER")
@DisplayName("Edge Cases and Error Paths")
class EdgeCaseTest extends BaseIntegrationTest {

    private static final String TEST_TENANT_ID = "test-tenant-edge-cases";

    @BeforeEach
    void setTenantContext() {
        TenantContext.setTenantId(TEST_TENANT_ID);
    }

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    @Nested
    @DisplayName("Malformed Requests")
    class MalformedRequests {
        @Test
        @DisplayName("malformed product JSON returns 400")
        void createProduct_withMalformedJson_thenReturns400() throws Exception {
            mockMvc.perform(post("/api/v1/products")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\": \"Product\", \"price\": }"))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.status").value(400));
        }

        @Test
        @DisplayName("missing required product fields returns 400")
        void createProduct_withMissingFields_thenReturns400() throws Exception {
            mockMvc.perform(post("/api/v1/products")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"description\": \"Test\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Validation failed"));
        }
    }

    @Nested
    @DisplayName("Untrusted Identifiers")
    class UntrustedIdentifiers {

        @Test
        @DisplayName("unknown product identifier returns 404")
        void getProduct_withUnknownId_thenReturns404() throws Exception {
            mockMvc.perform(get("/api/v1/products/{id}", "not-a-valid-id"))
                    .andExpect(status().isNotFound())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.status").value(404));
        }

        @Test
        @DisplayName("SQL-like category identifier is treated as data and returns 404")
        void getCategory_withSqlLikeId_thenReturns404() throws Exception {
            mockMvc.perform(get("/api/v1/categories/{id}", "1' OR '1'='1"))
                    .andExpect(status().isNotFound())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.status").value(404));
        }
    }
    @Nested
    @DisplayName("Null and Empty Values")
    class NullAndEmptyValues {

        @Test
        @DisplayName("null required product field returns 400")
        void createProduct_withNullRequiredField_thenReturns400() throws Exception {
            CreateProductRequest request = new CreateProductRequest(
                    null, "Product Name", "Description", null,
                    BigDecimal.TEN, "unit");

            mockMvc.perform(post("/api/v1/products")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Validation failed"));
        }

        @Test
        @DisplayName("empty required product field returns 400")
        void createProduct_withEmptyRequiredField_thenReturns400() throws Exception {
            CreateProductRequest request = new CreateProductRequest(
                    "", "Product Name", "Description", null,
                    BigDecimal.TEN, "unit");

            mockMvc.perform(post("/api/v1/products")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Validation failed"));
        }
    }
}
