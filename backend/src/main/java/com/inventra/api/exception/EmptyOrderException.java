package com.inventra.api.exception;

public class EmptyOrderException extends RuntimeException {
    
    public EmptyOrderException(String orderId) {
        super(String.format("Order %s cannot be submitted without items", orderId));
    }
}
