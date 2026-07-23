package com.inventra.api.exception;

import com.inventra.api.entity.OrderStatus;

public class OrderNotEditableException extends RuntimeException {
    
    public OrderNotEditableException(String orderId, OrderStatus status) {
        super(String.format("Order %s cannot be edited in status %s. Only DRAFT orders can be modified.", orderId, status));
    }
}
