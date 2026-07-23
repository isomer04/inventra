package com.inventra.api.report.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record StockMovementReportResponse(
    String groupBy,
    LocalDate startDate,
    LocalDate endDate,
    List<MovementGroupResponse> movements,
    Instant generatedAt
) {
    public StockMovementReportResponse {
        movements = movements != null ? List.copyOf(movements) : null;
    }
}
