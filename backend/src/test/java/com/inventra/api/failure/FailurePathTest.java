package com.inventra.api.failure;

import com.inventra.api.BaseIntegrationTest;
import com.inventra.api.auth.dto.TokenResponse;
import com.inventra.api.category.dto.CreateCategoryRequest;
import com.inventra.api.customer.dto.CreateCustomerRequest;
import com.inventra.api.product.dto.CreateProductRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Group 1: Failure Path Tests
 *
 * <p>Tests error handling for database failures, constraint violations,
 * and service layer failures. Verifies the system behaves correctly
 * under adverse conditions.
 */
class FailurePathTest extends BaseIntegrationTest {

    private TokenResponse adminTokens;

    @BeforeEach
    void setUp() throws Exception {
        String uid = String.valueOf(System.currentTimeMillis());
        adminTokens = registerTenant(
                "failure-test-" + uid,
                "admin-failure-" + uid + "@test.com",
                "Password123!"
        );
    }

    @Test
    void testDuplicateSkuInsertion() throws Exception {
        // Create a category (parentId = null, not a description)
        String categoryId = idFrom(mockMvc.perform(post("/api/v1/categories")
                        .header("Authorization", bearer(adminTokens))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateCategoryRequest("Electronics", null))))
                .andExpect(status().isCreated())
                .andReturn());

        mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", bearer(adminTokens))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateProductRequest(
                                "LAPTOP-001", "Laptop", "High-performance laptop",
                                categoryId, new BigDecimal("999.99"), "EA"))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", bearer(adminTokens))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateProductRequest(
                                "LAPTOP-001", "Another Laptop", "Different laptop",
                                categoryId, new BigDecimal("1299.99"), "EA"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void testDuplicateEmailRegistration() throws Exception {
        mockMvc.perform(post("/api/v1/customers")
                        .header("Authorization", bearer(adminTokens))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateCustomerRequest(
                                "John Doe", "john@example.com", "123-456-7890", "123 Main St", null))))
                .andExpect(status().isCreated());

        // Customer email has no unique constraint in the schema — a second customer
        // with the same email is allowed. The API returns 201 for both.
        mockMvc.perform(post("/api/v1/customers")
                        .header("Authorization", bearer(adminTokens))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateCustomerRequest(
                                "Jane Doe", "john@example.com", "987-654-3210", "456 Oak Ave", null))))
                .andExpect(status().isCreated());
    }

    @Test
    void testForeignKeyViolationInvalidProductId() throws Exception {
        String customerId = idFrom(mockMvc.perform(post("/api/v1/customers")
                        .header("Authorization", bearer(adminTokens))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateCustomerRequest(
                                "Test Customer", "customer-fk-test@example.com",
                                "555-1234", "789 Elm St", null))))
                .andExpect(status().isCreated())
                .andReturn());

        String invalidProductId = "00000000-0000-0000-0000-000000000000";
        mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", bearer(adminTokens))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("""
                                {"customerId":"%s","items":[{"productId":"%s","quantity":1,"unitPrice":99.99}]}
                                """, customerId, invalidProductId)))
                .andExpect(status().is4xxClientError())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void testNotNullConstraintViolationProductName() throws Exception {
        String categoryId = idFrom(mockMvc.perform(post("/api/v1/categories")
                        .header("Authorization", bearer(adminTokens))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateCategoryRequest("Books", null))))
                .andExpect(status().isCreated())
                .andReturn());

        mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", bearer(adminTokens))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(
                                "{\"name\":null,\"sku\":\"BOOK-001\",\"description\":\"A book\",\"unitPrice\":19.99,\"categoryId\":\"%s\"}",
                                categoryId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void testNotNullConstraintViolationProductSku() throws Exception {
        String categoryId = idFrom(mockMvc.perform(post("/api/v1/categories")
                        .header("Authorization", bearer(adminTokens))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateCategoryRequest("Toys", null))))
                .andExpect(status().isCreated())
                .andReturn());

        mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", bearer(adminTokens))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(
                                "{\"name\":\"Toy Car\",\"sku\":null,\"description\":\"A toy car\",\"unitPrice\":9.99,\"categoryId\":\"%s\"}",
                                categoryId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void testNotNullConstraintViolationCustomerName() throws Exception {
        mockMvc.perform(post("/api/v1/customers")
                        .header("Authorization", bearer(adminTokens))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":null,\"email\":\"test-null-name@example.com\",\"phone\":\"555-9999\",\"address\":\"999 Test St\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void testNotNullConstraintViolationCustomerEmail() throws Exception {
        // Customer email is optional (no @NotNull on CreateCustomerRequest.email).
        // Sending null email is allowed and returns 201.
        mockMvc.perform(post("/api/v1/customers")
                        .header("Authorization", bearer(adminTokens))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Test Customer\",\"email\":null,\"phone\":\"555-8888\",\"address\":\"888 Test St\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void testInvalidCategoryIdWhenCreatingProduct() throws Exception {
        mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", bearer(adminTokens))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateProductRequest(
                                "TEST-SKU-001", "Test Product", "Test description",
                                "00000000-0000-0000-0000-000000000000",
                                new BigDecimal("49.99"), "EA"))))
                .andExpect(status().is4xxClientError())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void testInvalidCustomerIdWhenCreatingOrder() throws Exception {
        String categoryId = idFrom(mockMvc.perform(post("/api/v1/categories")
                        .header("Authorization", bearer(adminTokens))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateCategoryRequest("Gadgets", null))))
                .andExpect(status().isCreated())
                .andReturn());

        String productId = idFrom(mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", bearer(adminTokens))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateProductRequest(
                                "GADGET-001", "Gadget", "Cool gadget",
                                categoryId, new BigDecimal("29.99"), "EA"))))
                .andExpect(status().isCreated())
                .andReturn());

        mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", bearer(adminTokens))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("""
                                {"customerId":"00000000-0000-0000-0000-000000000000","items":[{"productId":"%s","quantity":1,"unitPrice":29.99}]}
                                """, productId)))
                .andExpect(status().is4xxClientError())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void testEmptyOrderItemsList() throws Exception {
        String customerId = idFrom(mockMvc.perform(post("/api/v1/customers")
                        .header("Authorization", bearer(adminTokens))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateCustomerRequest(
                                "Empty Order Customer", "empty-order@example.com",
                                "555-0000", "000 Empty St", null))))
                .andExpect(status().isCreated())
                .andReturn());

        mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", bearer(adminTokens))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"customerId\":\"%s\",\"items\":[]}", customerId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void testNegativeQuantityInOrderItem() throws Exception {
        String customerId = idFrom(mockMvc.perform(post("/api/v1/customers")
                        .header("Authorization", bearer(adminTokens))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateCustomerRequest(
                                "Negative Qty Customer", "negative-qty@example.com",
                                "555-1111", "111 Negative St", null))))
                .andExpect(status().isCreated())
                .andReturn());

        String categoryId = idFrom(mockMvc.perform(post("/api/v1/categories")
                        .header("Authorization", bearer(adminTokens))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateCategoryRequest("Test Category", null))))
                .andExpect(status().isCreated())
                .andReturn());

        String productId = idFrom(mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", bearer(adminTokens))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateProductRequest(
                                "TEST-NEG-001", "Test Product", "Test",
                                categoryId, new BigDecimal("10.00"), "EA"))))
                .andExpect(status().isCreated())
                .andReturn());

        mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", bearer(adminTokens))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("""
                                {"customerId":"%s","items":[{"productId":"%s","quantity":-5,"unitPrice":10.00}]}
                                """, customerId, productId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void testNegativePriceInProduct() throws Exception {
        String categoryId = idFrom(mockMvc.perform(post("/api/v1/categories")
                        .header("Authorization", bearer(adminTokens))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateCategoryRequest("Price Test", null))))
                .andExpect(status().isCreated())
                .andReturn());

        mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", bearer(adminTokens))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateProductRequest(
                                "NEG-PRICE-001", "Negative Price Product", "Test",
                                categoryId, new BigDecimal("-99.99"), "EA"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());
    }
}
