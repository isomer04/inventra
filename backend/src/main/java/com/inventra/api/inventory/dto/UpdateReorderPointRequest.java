package com.inventra.api.inventory.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateReorderPointRequest {

    @NotNull(message = "Reorder point is required")
    @Min(value = 0, message = "Reorder point must be greater than or equal to 0")
    private Integer reorderPoint;
}
