package com.inventra.api.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Request to create an order item")
public class CreateOrderItemRequest {
    
    @NotBlank(message = "Product ID is required")
    @Schema(description = "Product ID", example = "123e4567-e89b-12d3-a456-426614174000")
    private String productId;
    
    // The upper bound is not just a business rule: downstream stock arithmetic such as
    // `quantityReserved + quantity` is int-based and would overflow before the
    // insufficient-stock check could reject an Integer.MAX_VALUE request. Bounding at the
    // validation boundary keeps that arithmetic safely in range.
    @NotNull(message = "Quantity is required")
    @Min(value = 1, message = "Quantity must be at least 1")
    @Max(value = 1_000_000, message = "Quantity must not exceed 1,000,000")
    @Schema(description = "Quantity to order", example = "10", minimum = "1", maximum = "1000000")
    private Integer quantity;
}
