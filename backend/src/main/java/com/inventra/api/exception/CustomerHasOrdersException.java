package com.inventra.api.exception;

/**
 * Exception thrown when attempting to delete a customer that has associated orders.
 */
public class CustomerHasOrdersException extends RuntimeException {
    
    public CustomerHasOrdersException(String customerId) {
        super("Cannot delete customer with ID " + customerId + " because they have existing orders");
    }
    
    public CustomerHasOrdersException(String message, Throwable cause) {
        super(message, cause);
    }
}
