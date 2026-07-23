package com.inventra.api.inventory.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdjustStockRequest {

    @NotNull(message = "Quantity is required")
    @Min(value = -100000, message = "Adjustment quantity cannot be less than -100,000")
    @Max(value = 100000, message = "Adjustment quantity cannot exceed 100,000")
    private Integer quantity;

    @NotBlank(message = "Notes are required for adjustments")
    @Size(max = 500, message = "Notes must be at most 500 characters")
    private String notes;
}
