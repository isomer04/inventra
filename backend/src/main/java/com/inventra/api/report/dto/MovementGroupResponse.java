package com.inventra.api.report.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.inventra.api.entity.MovementType;

import java.time.LocalDate;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record MovementGroupResponse(
    MovementType type,
    LocalDate date,
    Long count,
    Long totalQuantity
) {}
