package com.inventra.api.report.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record OrderSummaryResponse(
    LocalDate startDate,
    LocalDate endDate,
    List<OrderStatusGroupResponse> ordersByStatus,
    Long totalOrders,
    BigDecimal totalGmv,
    Double averageFulfillmentTimeHours,
    Instant generatedAt
) {
    public OrderSummaryResponse {
        ordersByStatus = ordersByStatus != null ? List.copyOf(ordersByStatus) : null;
    }
}
