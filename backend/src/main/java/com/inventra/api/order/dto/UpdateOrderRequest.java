package com.inventra.api.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Request to update a draft order")
public class UpdateOrderRequest {
    
    @Schema(description = "Customer ID (optional)", example = "123e4567-e89b-12d3-a456-426614174000")
    private String customerId;
    
    @Valid
    @Schema(description = "List of order items (replaces all existing items if provided)")
    private List<@NotNull CreateOrderItemRequest> items;
    
    @Size(max = 2000, message = "Notes cannot exceed 2000 characters")
    @Schema(description = "Optional notes for the order", example = "Updated delivery instructions")
    private String notes;
}
