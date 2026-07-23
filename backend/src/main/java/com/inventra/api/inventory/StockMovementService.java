package com.inventra.api.inventory;

import com.inventra.api.entity.InventoryItem;
import com.inventra.api.entity.MovementType;
import com.inventra.api.entity.StockMovement;
import com.inventra.api.exception.ConcurrentUpdateException;
import com.inventra.api.exception.InsufficientStockException;
import com.inventra.api.exception.InvalidQuantityException;
import com.inventra.api.inventory.dto.StockMovementResponse;
import com.inventra.api.repository.InventoryItemRepository;
import com.inventra.api.repository.StockMovementRepository;
import com.inventra.api.tenant.TenantContext;
import com.inventra.api.util.LogSanitizer;
import jakarta.persistence.OptimisticLockException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class StockMovementService {

    private final StockMovementRepository stockMovementRepository;
    private final InventoryItemRepository inventoryItemRepository;
    private final InventoryService inventoryService;
    private final StockMovementMapper stockMovementMapper;

    @Transactional
    public StockMovementResponse receiveStock(String productId, Integer quantity, String notes, String userId) {
        if (quantity <= 0) {
            throw new InvalidQuantityException("Receipt quantity must be greater than 0");
        }

        String tenantId = TenantContext.requireTenantId();
        log.info("Receiving {} units of product: {} for tenant: {}",
                quantity, LogSanitizer.sanitize(productId), LogSanitizer.sanitize(tenantId));

        return executeMovement(() -> {
            InventoryItem item = inventoryService.getInventoryItemEntity(productId);
            item.setQuantityOnHand(item.getQuantityOnHand() + quantity);
            inventoryItemRepository.save(item);

            return saveMovement(MovementSpec.standalone(tenantId, productId,
                    MovementType.RECEIPT, quantity, notes, userId));
        });
    }

    @Transactional
    public StockMovementResponse adjustStock(String productId, Integer quantity, String notes, String userId) {
        String tenantId = TenantContext.requireTenantId();
        log.info("Adjusting stock by {} units for product: {} in tenant: {}",
                quantity, LogSanitizer.sanitize(productId), LogSanitizer.sanitize(tenantId));

        return executeMovement(() -> {
            InventoryItem item = inventoryService.getInventoryItemEntity(productId);
            int newQuantity = item.getQuantityOnHand() + quantity;

            if (newQuantity < 0) {
                throw new InsufficientStockException(String.format(
                        "Adjustment would result in negative stock. Current: %d, Adjustment: %d",
                        item.getQuantityOnHand(), quantity));
            }
            if (newQuantity < item.getQuantityReserved()) {
                throw new InsufficientStockException(String.format(
                        "Adjustment would result in insufficient stock to cover reservations. "
                                + "Current: %d, Reserved: %d, Adjustment: %d",
                        item.getQuantityOnHand(), item.getQuantityReserved(), quantity));
            }

            item.setQuantityOnHand(newQuantity);
            inventoryItemRepository.save(item);

            return saveMovement(MovementSpec.standalone(tenantId, productId,
                    MovementType.ADJUSTMENT, quantity, notes, userId));
        });
    }

    @Transactional
    public StockMovementResponse reserveStock(String productId, Integer quantity,
                                               String orderId, String userId) {
        if (quantity <= 0) {
            throw new InvalidQuantityException("Reservation quantity must be greater than 0");
        }

        String tenantId = TenantContext.requireTenantId();
        log.info("Reserving {} units of product: {} for order: {} in tenant: {}",
                quantity, LogSanitizer.sanitize(productId),
                LogSanitizer.sanitize(orderId), LogSanitizer.sanitize(tenantId));

        return executeMovement(() -> {
            InventoryItem item = inventoryService.getInventoryItemEntity(productId);

            if (item.getAvailableStock() < quantity) {
                throw new InsufficientStockException(String.format(
                        "Insufficient stock to reserve. Available: %d, Requested: %d",
                        item.getAvailableStock(), quantity));
            }

            item.setQuantityReserved(item.getQuantityReserved() + quantity);
            inventoryItemRepository.save(item);

            // Stored as negative to represent a reduction in available stock
            return saveMovement(MovementSpec.forOrder(tenantId, productId,
                    MovementType.RESERVATION, -quantity, orderId, userId));
        });
    }

    @Transactional
    public StockMovementResponse releaseReservation(String productId, Integer quantity,
                                                     String orderId, String userId) {
        if (quantity <= 0) {
            throw new InvalidQuantityException("Release quantity must be greater than 0");
        }

        String tenantId = TenantContext.requireTenantId();
        log.info("Releasing {} units of product: {} for order: {} in tenant: {}",
                quantity, LogSanitizer.sanitize(productId),
                LogSanitizer.sanitize(orderId), LogSanitizer.sanitize(tenantId));

        return executeMovement(() -> {
            InventoryItem item = inventoryService.getInventoryItemEntity(productId);

            if (item.getQuantityReserved() < quantity) {
                throw new InvalidQuantityException(String.format(
                        "Cannot release more than reserved. Reserved: %d, Requested: %d",
                        item.getQuantityReserved(), quantity));
            }

            item.setQuantityReserved(item.getQuantityReserved() - quantity);
            inventoryItemRepository.save(item);

            return saveMovement(MovementSpec.forOrder(tenantId, productId,
                    MovementType.RESERVATION_RELEASE, quantity, orderId, userId));
        });
    }

    @Transactional
    public StockMovementResponse deductStock(String productId, Integer quantity,
                                              String orderId, String userId) {
        if (quantity <= 0) {
            throw new InvalidQuantityException("Deduction quantity must be greater than 0");
        }

        String tenantId = TenantContext.requireTenantId();
        log.info("Deducting {} units of product: {} for order: {} in tenant: {}",
                quantity, LogSanitizer.sanitize(productId),
                LogSanitizer.sanitize(orderId), LogSanitizer.sanitize(tenantId));

        return executeMovement(() -> {
            InventoryItem item = inventoryService.getInventoryItemEntity(productId);

            if (item.getQuantityOnHand() < quantity) {
                throw new InsufficientStockException(String.format(
                        "Insufficient stock to deduct. On hand: %d, Requested: %d",
                        item.getQuantityOnHand(), quantity));
            }
            if (item.getQuantityReserved() < quantity) {
                throw new InvalidQuantityException(String.format(
                        "Cannot deduct more than reserved. Reserved: %d, Requested: %d",
                        item.getQuantityReserved(), quantity));
            }

            item.setQuantityOnHand(item.getQuantityOnHand() - quantity);
            item.setQuantityReserved(item.getQuantityReserved() - quantity);
            inventoryItemRepository.save(item);

            // Stored as negative to represent a reduction in on-hand stock
            return saveMovement(MovementSpec.forOrder(tenantId, productId,
                    MovementType.DEDUCTION, -quantity, orderId, userId));
        });
    }

    @Transactional(readOnly = true)
    public Page<StockMovementResponse> getMovements(Pageable pageable, String productId,
                                                     MovementType type,
                                                     java.time.Instant startDate,
                                                     java.time.Instant endDate) {
        String tenantId = TenantContext.requireTenantId();
        log.debug("Fetching movements for tenant: {} with filters", LogSanitizer.sanitize(tenantId));
        return stockMovementRepository.findByFilters(tenantId, productId, type, startDate, endDate, pageable)
                .map(stockMovementMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<StockMovementResponse> getMovementsByProductId(String productId, Pageable pageable) {
        String tenantId = TenantContext.requireTenantId();
        log.debug("Fetching movements for product: {} in tenant: {}",
                LogSanitizer.sanitize(productId), LogSanitizer.sanitize(tenantId));
        return stockMovementRepository.findByTenantIdAndProductId(tenantId, productId, pageable)
                .map(stockMovementMapper::toResponse);
    }

    /**
     * Wraps a stock-mutation lambda with optimistic-lock exception translation.
     * All public write methods delegate through here so the catch block lives
     * in exactly one place.
     */
    private StockMovementResponse executeMovement(MovementAction action) {
        try {
            return action.execute();
        } catch (OptimisticLockException | ObjectOptimisticLockingFailureException ex) {
            throw new ConcurrentUpdateException("Concurrent update detected. Please retry the operation.");
        }
    }

    /** Builds and persists a {@link StockMovement} from a {@link MovementSpec}. */
    private StockMovementResponse saveMovement(MovementSpec spec) {
        StockMovement movement = StockMovement.builder()
                .tenantId(spec.tenantId())
                .productId(spec.productId())
                .type(spec.type())
                .quantity(spec.quantity())
                .referenceId(spec.referenceId())
                .referenceType(spec.referenceType())
                .notes(spec.notes())
                .createdBy(spec.userId())
                .build();
        return stockMovementMapper.toResponse(stockMovementRepository.save(movement));
    }

    /** Functional interface for the stock-mutation lambdas passed to {@link #executeMovement}. */
    @FunctionalInterface
    private interface MovementAction {
        StockMovementResponse execute();
    }
}
