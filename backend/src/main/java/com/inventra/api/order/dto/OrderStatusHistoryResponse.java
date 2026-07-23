package com.inventra.api.order.dto;

import com.inventra.api.entity.OrderStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Order status history entry")
public class OrderStatusHistoryResponse {
    
    @Schema(description = "History entry ID", example = "123e4567-e89b-12d3-a456-426614174000")
    private String id;
    
    @Schema(description = "Previous status (null for initial creation)", example = "DRAFT")
    private OrderStatus fromStatus;
    
    @Schema(description = "New status", example = "SUBMITTED")
    private OrderStatus toStatus;
    
    @Schema(description = "User ID who made the change", example = "123e4567-e89b-12d3-a456-426614174000")
    private String changedById;
    
    @Schema(description = "Name of user who made the change", example = "John Doe")
    private String changedByName;
    
    @Schema(description = "Timestamp of the change", example = "2026-05-04T10:30:00Z")
    private Instant changedAt;
    
    @Schema(description = "Optional notes for the transition", example = "Approved by manager")
    private String notes;
}
