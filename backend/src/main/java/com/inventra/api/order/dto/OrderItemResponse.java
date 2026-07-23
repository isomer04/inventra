package com.inventra.api.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Order item details")
public class OrderItemResponse {
    
    @Schema(description = "Order item ID", example = "123e4567-e89b-12d3-a456-426614174000")
    private String id;
    
    @Schema(description = "Product ID", example = "123e4567-e89b-12d3-a456-426614174000")
    private String productId;
    
    @Schema(description = "Product SKU", example = "LAPTOP-001")
    private String productSku;
    
    @Schema(description = "Product name", example = "Dell XPS 15")
    private String productName;
    
    @Schema(description = "Quantity ordered", example = "10")
    private Integer quantity;
    
    @Schema(description = "Unit price at time of order", example = "1299.99")
    private BigDecimal unitPrice;
    
    @Schema(description = "Total price for this line item", example = "12999.90")
    private BigDecimal totalPrice;
}
