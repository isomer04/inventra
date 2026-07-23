package com.inventra.api.exception;

import com.inventra.api.entity.OrderStatus;

public class InvalidOrderTransitionException extends RuntimeException {
    
    public InvalidOrderTransitionException(OrderStatus from, OrderStatus to) {
        super(String.format("Invalid order transition from %s to %s", from, to));
    }
    
    public InvalidOrderTransitionException(String message) {
        super(message);
    }
}
