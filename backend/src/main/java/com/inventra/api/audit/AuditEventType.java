package com.inventra.api.audit;

public enum AuditEventType {
    TENANT_REGISTERED,
    USER_CREATED,
    USER_ROLE_CHANGED,
    USER_STATUS_CHANGED,
    USER_PASSWORD_CHANGED,
    USER_DELETED,
    ORDER_SUBMITTED,
    ORDER_APPROVED,
    ORDER_REJECTED,
    ORDER_PICKING_STARTED,
    ORDER_SHIPPED,
    ORDER_DELIVERED,
    ORDER_CANCELLED,
    REFRESH_TOKEN_REUSE_DETECTED;

    @Override
    public String toString() {
        return this.name();
    }
}
