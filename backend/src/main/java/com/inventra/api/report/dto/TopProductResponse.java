package com.inventra.api.report.dto;

import java.math.BigDecimal;

public record TopProductResponse(
    String productId,
    String sku,
    String name,
    Long orderCount,
    Long totalQuantitySold,
    BigDecimal totalRevenue
) {}
