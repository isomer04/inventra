package com.inventra.api.product.dto;

import com.inventra.api.entity.ProductStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record ProductResponse(
        String id,
        String sku,
        String name,
        String description,
        String categoryId,
        String categoryName,
        BigDecimal unitPrice,
        String unitOfMeasure,
        ProductStatus status,
        Instant createdAt,
        Instant updatedAt
) {}
