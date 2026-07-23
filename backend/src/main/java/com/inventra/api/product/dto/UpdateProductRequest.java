package com.inventra.api.product.dto;

import com.inventra.api.entity.ProductStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record UpdateProductRequest(
        @Size(max = 100, message = "SKU must not exceed 100 characters")
        String sku,

        @Size(max = 200, message = "Name must not exceed 200 characters")
        String name,

        @Size(max = 10000, message = "Description must not exceed 10000 characters")
        String description,

        String categoryId,

        @DecimalMin(value = "0.0", inclusive = true, message = "Unit price must be >= 0")
        BigDecimal unitPrice,

        @Size(max = 30, message = "Unit of measure must not exceed 30 characters")
        String unitOfMeasure,

        ProductStatus status
) {}
