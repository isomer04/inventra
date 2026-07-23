package com.inventra.api.order;

import com.inventra.api.entity.OrderStatusHistory;
import com.inventra.api.order.dto.OrderStatusHistoryResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface OrderStatusHistoryMapper {
    
    @Mapping(target = "changedById", source = "changedBy.id")
    @Mapping(target = "changedByName", expression = "java(history.getChangedBy().getFirstName() + \" \" + history.getChangedBy().getLastName())")
    @Mapping(target = "changedAt", source = "changedAt")
    OrderStatusHistoryResponse toResponse(OrderStatusHistory history);
}
