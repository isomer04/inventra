package com.inventra.api.product.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record CreateProductRequest(
        @NotBlank(message = "SKU is required")
        @Size(max = 100, message = "SKU must not exceed 100 characters")
        String sku,

        @NotBlank(message = "Name is required")
        @Size(max = 200, message = "Name must not exceed 200 characters")
        String name,

        @Size(max = 10000, message = "Description must not exceed 10000 characters")
        String description,

        String categoryId,

        @NotNull(message = "Unit price is required")
        @DecimalMin(value = "0.0", inclusive = true, message = "Unit price must be >= 0")
        BigDecimal unitPrice,

        @Size(max = 30, message = "Unit of measure must not exceed 30 characters")
        String unitOfMeasure
) {}
