package com.inventra.api.exception;

public class ConcurrentUpdateException extends RuntimeException {
    public ConcurrentUpdateException(String message) {
        super(message);
    }
}
