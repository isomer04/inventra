package com.inventra.api.inventory;

import com.inventra.api.entity.MovementType;

/**
 * Value object that groups the parameters needed to record a single stock movement.
 *
 * <p>Replaces the 8-argument {@code createMovement} parameter list in
 * {@link StockMovementService}, keeping call sites readable and making it
 * easy to add optional fields (e.g. a reason code) without changing every caller.
 */
public record MovementSpec(
        String tenantId,
        String productId,
        MovementType type,
        Integer quantity,
        String referenceId,
        String referenceType,
        String notes,
        String userId
) {

    /** Convenience factory for movements that have no order reference. */
    public static MovementSpec standalone(String tenantId, String productId,
                                          MovementType type, Integer quantity,
                                          String notes, String userId) {
        return new MovementSpec(tenantId, productId, type, quantity, null, null, notes, userId);
    }

    /** Convenience factory for movements tied to an order reference. */
    public static MovementSpec forOrder(String tenantId, String productId,
                                        MovementType type, Integer quantity,
                                        String orderId, String userId) {
        return new MovementSpec(tenantId, productId, type, quantity, orderId, "ORDER", null, userId);
    }
}
