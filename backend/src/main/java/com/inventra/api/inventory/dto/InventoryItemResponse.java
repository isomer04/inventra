package com.inventra.api.inventory.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventoryItemResponse {
    private String id;
    private String productId;
    private String productName;
    private String productSku;
    private Integer quantityOnHand;
    private Integer quantityReserved;
    private Integer availableStock;
    private Integer reorderPoint;
    private Instant lastUpdated;
}
