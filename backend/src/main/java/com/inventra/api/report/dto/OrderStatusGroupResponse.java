package com.inventra.api.report.dto;

import com.inventra.api.entity.OrderStatus;

import java.math.BigDecimal;

public record OrderStatusGroupResponse(
    OrderStatus status,
    Long count,
    BigDecimal totalAmount
) {}
