package com.inventra.api.order.dto;

import com.inventra.api.entity.OrderStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Order details with items")
public class OrderResponse {
    
    @Schema(description = "Order ID", example = "123e4567-e89b-12d3-a456-426614174000")
    private String id;
    
    @Schema(description = "Order number", example = "ORD-2026-00042")
    private String orderNumber;
    
    @Schema(description = "Customer ID", example = "123e4567-e89b-12d3-a456-426614174000")
    private String customerId;
    
    @Schema(description = "Customer name", example = "Acme Corp")
    private String customerName;
    
    @Schema(description = "Order status", example = "SUBMITTED")
    private OrderStatus status;
    
    @Schema(description = "Total order amount", example = "12999.90")
    private BigDecimal totalAmount;
    
    @Schema(description = "Order notes", example = "Urgent delivery required")
    private String notes;
    
    @Schema(description = "User ID who created the order", example = "123e4567-e89b-12d3-a456-426614174000")
    private String createdById;
    
    @Schema(description = "Name of user who created the order", example = "John Doe")
    private String createdByName;
    
    @Schema(description = "Order creation timestamp", example = "2026-05-04T10:30:00Z")
    private Instant createdAt;
    
    @Schema(description = "Order last update timestamp", example = "2026-05-04T11:45:00Z")
    private Instant updatedAt;
    
    @Schema(description = "Order items")
    private List<OrderItemResponse> items;
}
