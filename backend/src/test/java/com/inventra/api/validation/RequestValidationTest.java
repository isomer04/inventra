package com.inventra.api.validation;

import com.inventra.api.BaseIntegrationTest;
import com.inventra.api.category.CategoryService;
import com.inventra.api.category.dto.CreateCategoryRequest;
import com.inventra.api.category.dto.UpdateCategoryRequest;
import com.inventra.api.customer.CustomerService;
import com.inventra.api.customer.dto.CreateCustomerRequest;
import com.inventra.api.customer.dto.UpdateCustomerRequest;
import com.inventra.api.entity.CustomerStatus;
import com.inventra.api.inventory.InventoryService;
import com.inventra.api.inventory.dto.AdjustStockRequest;
import com.inventra.api.inventory.dto.ReceiveStockRequest;
import com.inventra.api.product.ProductService;
import com.inventra.api.product.dto.CreateProductRequest;
import com.inventra.api.product.dto.UpdateProductRequest;
import com.inventra.api.user.UserService;
import com.inventra.api.user.dto.CreateUserRequest;
import com.inventra.api.user.dto.UpdateUserRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WithMockUser(roles = "ADMIN")
@DisplayName("Request Validation")
class RequestValidationTest extends BaseIntegrationTest {

    @MockitoBean
    private CategoryService categoryService;

    @MockitoBean
    private ProductService productService;

    @MockitoBean
    private CustomerService customerService;

    @MockitoBean
    private InventoryService inventoryService;

    @MockitoBean
    private UserService userService;

    @Test
    @DisplayName("POST /api/v1/categories with blank name returns 400")
    void createCategory_whenNameIsBlank_thenReturns400() throws Exception {
        CreateCategoryRequest request = new CreateCategoryRequest("", null);

        mockMvc.perform(post("/api/v1/categories")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").exists())
            .andExpect(jsonPath("$.violations[?(@.field == 'name')]").exists());
    }

    @Test
    @DisplayName("POST /api/v1/categories with name > 100 chars returns 400")
    void createCategory_whenNameTooLong_thenReturns400() throws Exception {
        String longName = "a".repeat(101);
        CreateCategoryRequest request = new CreateCategoryRequest(longName, null);

        mockMvc.perform(post("/api/v1/categories")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").exists())
            .andExpect(jsonPath("$.violations[?(@.field == 'name')]").exists());
    }

    @Test
    @DisplayName("PUT /api/v1/categories/{id} with blank name returns 400")
    void updateCategory_whenNameIsBlank_thenReturns400() throws Exception {
        UpdateCategoryRequest request = new UpdateCategoryRequest("", null);

        mockMvc.perform(put("/api/v1/categories/test-id")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").exists())
            .andExpect(jsonPath("$.violations[?(@.field == 'name')]").exists());
    }

    @Test
    @DisplayName("POST /api/v1/products with blank name returns 400")
    void createProduct_whenNameIsBlank_thenReturns400() throws Exception {
        CreateProductRequest request = new CreateProductRequest(
            "SKU-001", "", null, null, BigDecimal.TEN, null);

        mockMvc.perform(post("/api/v1/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").exists())
            .andExpect(jsonPath("$.violations[?(@.field == 'name')]").exists());
    }

    @Test
    @DisplayName("POST /api/v1/products with blank SKU returns 400")
    void createProduct_whenSkuIsBlank_thenReturns400() throws Exception {
        CreateProductRequest request = new CreateProductRequest(
            "", "Product Name", null, null, BigDecimal.TEN, null);

        mockMvc.perform(post("/api/v1/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").exists())
            .andExpect(jsonPath("$.violations[?(@.field == 'sku')]").exists());
    }

    @Test
    @DisplayName("POST /api/v1/products with negative price returns 400")
    void createProduct_whenPriceIsNegative_thenReturns400() throws Exception {
        CreateProductRequest request = new CreateProductRequest(
            "SKU-001", "Product Name", null, null, BigDecimal.valueOf(-10), null);

        mockMvc.perform(post("/api/v1/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").exists())
            .andExpect(jsonPath("$.violations[?(@.field == 'unitPrice')]").exists());
    }

    @Test
    @DisplayName("POST /api/v1/products with null price returns 400")
    void createProduct_whenPriceIsNull_thenReturns400() throws Exception {
        CreateProductRequest request = new CreateProductRequest(
            "SKU-001", "Product Name", null, null, null, null);

        mockMvc.perform(post("/api/v1/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").exists())
            .andExpect(jsonPath("$.violations[?(@.field == 'unitPrice')]").exists());
    }

    @Test
    @DisplayName("PUT /api/v1/products/{id} with invalid fields returns 400")
    void updateProduct_whenFieldsInvalid_thenReturns400() throws Exception {
        UpdateProductRequest request = new UpdateProductRequest(
            null, "", null, null, BigDecimal.valueOf(-5), null, null);

        mockMvc.perform(put("/api/v1/products/test-id")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("POST /api/v1/customers with blank name returns 400")
    void createCustomer_whenNameIsBlank_thenReturns400() throws Exception {
        CreateCustomerRequest request = new CreateCustomerRequest(
            "", "test@example.com", null, null, null);

        mockMvc.perform(post("/api/v1/customers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").exists())
            .andExpect(jsonPath("$.violations[?(@.field == 'name')]").exists());
    }

    @Test
    @DisplayName("POST /api/v1/customers with invalid email returns 400")
    void createCustomer_whenEmailInvalid_thenReturns400() throws Exception {
        CreateCustomerRequest request = new CreateCustomerRequest(
            "Customer Name", "not-an-email", null, null, null);

        mockMvc.perform(post("/api/v1/customers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").exists())
            .andExpect(jsonPath("$.violations[?(@.field == 'email')]").exists());
    }

    @Test
    @DisplayName("POST /api/v1/customers with name > 200 chars returns 400")
    void createCustomer_whenNameTooLong_thenReturns400() throws Exception {
        String longName = "a".repeat(201);
        CreateCustomerRequest request = new CreateCustomerRequest(
            longName, "test@example.com", null, null, null);

        mockMvc.perform(post("/api/v1/customers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").exists())
            .andExpect(jsonPath("$.violations[?(@.field == 'name')]").exists());
    }

    @Test
    @DisplayName("PUT /api/v1/customers/{id} with invalid email returns 400")
    void updateCustomer_whenEmailInvalid_thenReturns400() throws Exception {
        UpdateCustomerRequest request = new UpdateCustomerRequest(
            "Customer Name", "not-an-email", null, null, null, CustomerStatus.ACTIVE);

        mockMvc.perform(put("/api/v1/customers/test-id")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").exists())
            .andExpect(jsonPath("$.violations[?(@.field == 'email')]").exists());
    }

    @Test
    @DisplayName("PUT /api/v1/inventory/{productId}/receive with negative quantity returns 400")
    void receiveStock_whenQuantityIsNegative_thenReturns400() throws Exception {
        ReceiveStockRequest request = new ReceiveStockRequest(-10, null);

        mockMvc.perform(put("/api/v1/inventory/product-id/receive")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").exists())
            .andExpect(jsonPath("$.violations[?(@.field == 'quantity')]").exists());
    }

    @Test
    @DisplayName("PUT /api/v1/inventory/{productId}/adjust with excessive quantity returns 400")
    void adjustStock_whenQuantityExceedsMaximum_thenReturns400() throws Exception {
        AdjustStockRequest request = new AdjustStockRequest(100_001, "Adjustment reason");

        mockMvc.perform(put("/api/v1/inventory/product-id/adjust")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").exists())
            .andExpect(jsonPath("$.violations[?(@.field == 'quantity')]").exists());
    }

    @Test
    @DisplayName("PUT /api/v1/inventory/{productId}/adjust with missing reason returns 400")
    void adjustStock_whenReasonIsMissing_thenReturns400() throws Exception {
        AdjustStockRequest request = new AdjustStockRequest(10, "");

        mockMvc.perform(put("/api/v1/inventory/product-id/adjust")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").exists())
            .andExpect(jsonPath("$.violations[?(@.field == 'notes')]").exists());
    }

    @Test
    @DisplayName("POST /api/v1/users with blank email returns 400")
    void createUser_whenEmailIsBlank_thenReturns400() throws Exception {
        CreateUserRequest request = new CreateUserRequest(
            "", "password123", "John", "Doe", com.inventra.api.entity.UserRole.VIEWER);

        mockMvc.perform(post("/api/v1/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").exists())
            .andExpect(jsonPath("$.violations[?(@.field == 'email')]").exists());
    }

    @Test
    @DisplayName("POST /api/v1/users with invalid email returns 400")
    void createUser_whenEmailInvalid_thenReturns400() throws Exception {
        CreateUserRequest request = new CreateUserRequest(
            "not-an-email", "password123", "John", "Doe", com.inventra.api.entity.UserRole.VIEWER);

        mockMvc.perform(post("/api/v1/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").exists())
            .andExpect(jsonPath("$.violations[?(@.field == 'email')]").exists());
    }

    @Test
    @DisplayName("POST /api/v1/users with short password returns 400")
    void createUser_whenPasswordTooShort_thenReturns400() throws Exception {
        CreateUserRequest request = new CreateUserRequest(
            "user@example.com", "123", "John", "Doe", com.inventra.api.entity.UserRole.VIEWER);

        mockMvc.perform(post("/api/v1/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").exists())
            .andExpect(jsonPath("$.violations[?(@.field == 'password')]").exists());
    }

    @Test
    @DisplayName("POST /api/v1/users with a password over 72 UTF-8 bytes returns 400")
    void createUser_whenPasswordExceeds72Utf8Bytes_thenReturns400() throws Exception {
        // 40 characters, 120 UTF-8 bytes. The character-counting @Size(max = 72) this
        // replaced accepted it, and BCrypt then hashed only the first 72 bytes — so the
        // password the user chose and the password that guarded the account differed.
        String multiByte = "字".repeat(40);
        assertThat(multiByte.length()).isLessThan(72);
        assertThat(multiByte.getBytes(StandardCharsets.UTF_8).length).isGreaterThan(72);

        CreateUserRequest request = new CreateUserRequest(
            "user@example.com", multiByte, "John", "Doe", com.inventra.api.entity.UserRole.VIEWER);

        mockMvc.perform(post("/api/v1/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.violations[?(@.field == 'password')]").exists());
    }

    @Test
    @DisplayName("PUT /api/v1/users/{id} with short password returns 400")
    void updateUser_whenPasswordTooShort_thenReturns400() throws Exception {
        UpdateUserRequest request = new UpdateUserRequest(
            "John", "Doe", "123", null, com.inventra.api.entity.UserRole.VIEWER, null);

        mockMvc.perform(put("/api/v1/users/test-id")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").exists())
            .andExpect(jsonPath("$.violations[?(@.field == 'password')]").exists());
    }
}
