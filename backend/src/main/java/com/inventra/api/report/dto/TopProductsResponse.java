package com.inventra.api.report.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record TopProductsResponse(
    Integer days,
    Integer limit,
    LocalDate startDate,
    LocalDate endDate,
    List<TopProductResponse> products,
    Instant generatedAt
) {
    public TopProductsResponse {
        products = products != null ? List.copyOf(products) : null;
    }
}
