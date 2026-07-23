package com.inventra.api.inventory.dto;

import com.inventra.api.entity.MovementType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockMovementResponse {
    private String id;
    private String productId;
    private String productName;
    private String productSku;
    private MovementType type;
    private Integer quantity;
    private String referenceId;
    private String referenceType;
    private String notes;
    private Instant createdAt;
    private String createdBy;
    private String createdByName;
}
