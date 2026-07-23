package com.inventra.api.entity;

public enum MovementType {
    RECEIPT,              // Stock received from supplier (quantity > 0)
    ADJUSTMENT,           // Manual correction (quantity can be + or -)
    RESERVATION,          // Stock reserved for order (increases quantity_reserved)
    RESERVATION_RELEASE,  // Reservation cancelled (decreases quantity_reserved)
    DEDUCTION             // Stock shipped/consumed (decreases both quantities)
}
