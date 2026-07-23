package com.inventra.api.entity;

public enum CustomerStatus {
    /**
     * Customer is active and can be used for orders.
     */
    ACTIVE,
    
    /**
     * Customer is inactive and should not be used for new orders.
     */
    INACTIVE
}
