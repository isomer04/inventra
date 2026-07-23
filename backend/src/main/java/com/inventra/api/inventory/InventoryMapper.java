package com.inventra.api.inventory;

import com.inventra.api.entity.InventoryItem;
import com.inventra.api.inventory.dto.InventoryItemResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface InventoryMapper {

    @Mapping(source = "product.name", target = "productName")
    @Mapping(source = "product.sku", target = "productSku")
    @Mapping(source = "availableStock", target = "availableStock")
    InventoryItemResponse toResponse(InventoryItem inventoryItem);
}
