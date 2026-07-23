package com.inventra.api.inventory;

import com.inventra.api.entity.StockMovement;
import com.inventra.api.inventory.dto.StockMovementResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface StockMovementMapper {

    @Mapping(source = "product.name", target = "productName")
    @Mapping(source = "product.sku", target = "productSku")
    @Mapping(source = "creator.firstName", target = "createdByName", defaultValue = "Unknown")
    StockMovementResponse toResponse(StockMovement stockMovement);
}
