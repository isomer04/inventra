package com.inventra.api.report.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record InventorySummaryResponse(
    Long totalSkus,
    BigDecimal totalStockValue,
    Long lowStockCount,
    Long totalQuantityOnHand,
    Long totalQuantityReserved,
    Long totalQuantityAvailable,
    Instant generatedAt
) {}
