package com.inventra.api.order;

import com.inventra.api.entity.OrderItem;
import com.inventra.api.order.dto.OrderItemResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface OrderItemMapper {
    
    @Mapping(target = "productId", source = "product.id")
    @Mapping(target = "productSku", source = "product.sku")
    @Mapping(target = "productName", source = "product.name")
    OrderItemResponse toResponse(OrderItem orderItem);
}
