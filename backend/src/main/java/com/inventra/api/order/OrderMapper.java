package com.inventra.api.order;

import com.inventra.api.entity.Order;
import com.inventra.api.order.dto.OrderResponse;
import com.inventra.api.order.dto.OrderSummaryResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = {OrderItemMapper.class})
public interface OrderMapper {

    @Mapping(target = "customerId", source = "customer.id")
    @Mapping(target = "customerName", source = "customer.name")
    @Mapping(target = "createdById", source = "createdBy.id")
    @Mapping(target = "createdByName", expression = "java(formatUserName(order))")
    @Mapping(target = "createdAt", source = "createdAt")
    @Mapping(target = "updatedAt", source = "updatedAt")
    OrderResponse toResponse(Order order);

    @Mapping(target = "customerId", source = "customer.id")
    @Mapping(target = "customerName", source = "customer.name")
    @Mapping(target = "createdById", source = "createdBy.id")
    @Mapping(target = "createdByName", expression = "java(formatUserName(order))")
    @Mapping(target = "createdAt", source = "createdAt")
    @Mapping(target = "updatedAt", source = "updatedAt")
    OrderSummaryResponse toSummaryResponse(Order order);

    default String formatUserName(Order order) {
        if (order.getCreatedBy() == null) {
            return null;
        }
        return order.getCreatedBy().getFirstName() + " " + order.getCreatedBy().getLastName();
    }
}
