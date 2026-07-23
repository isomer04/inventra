package com.inventra.api.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
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
@Schema(description = "Request to create a new order")
public class CreateOrderRequest {
    
    @NotBlank(message = "Customer ID is required")
    @Schema(description = "Customer ID", example = "123e4567-e89b-12d3-a456-426614174000")
    private String customerId;
    
    @NotEmpty(message = "Order must have at least one item")
    @Valid
    @Schema(description = "List of order items")
    private List<CreateOrderItemRequest> items;
    
    @Size(max = 2000, message = "Notes cannot exceed 2000 characters")
    @Schema(description = "Optional notes for the order", example = "Urgent delivery required")
    private String notes;
}
