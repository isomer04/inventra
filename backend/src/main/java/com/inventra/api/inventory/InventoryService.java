package com.inventra.api.inventory;

import com.inventra.api.entity.InventoryItem;
import com.inventra.api.entity.Product;
import com.inventra.api.entity.Tenant;
import com.inventra.api.exception.ResourceNotFoundException;
import com.inventra.api.inventory.dto.InventoryItemResponse;
import com.inventra.api.repository.InventoryItemRepository;
import com.inventra.api.tenant.TenantContext;
import com.inventra.api.util.LogSanitizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryService {

    private final InventoryItemRepository inventoryItemRepository;
    private final InventoryMapper inventoryMapper;

    @Transactional(readOnly = true)
    public Page<InventoryItemResponse> getAllInventory(Pageable pageable) {
        String tenantId = TenantContext.requireTenantId();
        log.debug("Fetching inventory for tenant: {}", LogSanitizer.sanitize(tenantId));
        
        return inventoryItemRepository.findByTenantId(tenantId, pageable)
                .map(inventoryMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public InventoryItemResponse getInventoryByProductId(String productId) {
        String tenantId = TenantContext.requireTenantId();
        log.debug("Fetching inventory for product: {} in tenant: {}", LogSanitizer.sanitize(productId), LogSanitizer.sanitize(tenantId));
        
        InventoryItem inventoryItem = inventoryItemRepository
                .findByTenantIdAndProductId(tenantId, productId)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory not found for product: " + productId));
        
        return inventoryMapper.toResponse(inventoryItem);
    }

    @Transactional(readOnly = true)
    public List<InventoryItemResponse> getLowStockItems() {
        String tenantId = TenantContext.requireTenantId();
        log.debug("Fetching low stock items for tenant: {}", LogSanitizer.sanitize(tenantId));
        
        return inventoryItemRepository.findLowStockItems(tenantId).stream()
                .map(inventoryMapper::toResponse)
                .toList();
    }

    @Transactional
    public InventoryItemResponse updateReorderPoint(String productId, Integer reorderPoint) {
        String tenantId = TenantContext.requireTenantId();
        log.info("Updating reorder point for product: {} to {} in tenant: {}", LogSanitizer.sanitize(productId), reorderPoint, LogSanitizer.sanitize(tenantId));
        
        InventoryItem inventoryItem = inventoryItemRepository
                .findByTenantIdAndProductId(tenantId, productId)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory not found for product: " + productId));
        
        inventoryItem.setReorderPoint(reorderPoint);
        InventoryItem saved = inventoryItemRepository.save(inventoryItem);
        
        return inventoryMapper.toResponse(saved);
    }

    @Transactional
    public void createInventoryForProduct(Product product) {
        Tenant tenant = product.getTenant();
        if (tenant == null) {
            throw new IllegalStateException("Product must have a tenant assigned before creating inventory");
        }
        String tenantId = tenant.getId();
        String productId = product.getId();
        
        log.info("Creating inventory for product: {} in tenant: {}", LogSanitizer.sanitize(productId), LogSanitizer.sanitize(tenantId));
        
        InventoryItem inventoryItem = InventoryItem.builder()
                .tenantId(tenantId)
                .productId(productId)
                .quantityOnHand(0)
                .quantityReserved(0)
                .reorderPoint(0)
                .build();
        
        try {
            inventoryItemRepository.save(inventoryItem);
        } catch (org.springframework.dao.DataIntegrityViolationException ex) {
            // Only suppress duplicate key violations on the unique constraint;
            // rethrow other constraint violations (FK, CHECK, etc.)
            Throwable cause = ex.getMostSpecificCause();

            boolean isDuplicateKey = (cause != null && cause.getMessage() != null &&
                                     (cause.getMessage().contains("uq_inventory_product") ||
                                      cause.getMessage().contains("Duplicate entry")));

            if (isDuplicateKey) {
                // Expected: concurrent creation - inventory already exists
                log.debug("Inventory already exists for product: {} (concurrent create)", LogSanitizer.sanitize(productId));
            } else {
                log.error("Unexpected constraint violation creating inventory for product: {}", LogSanitizer.sanitize(productId), ex);
                throw ex;
            }
        }
    }

    @Transactional(readOnly = true)
    public InventoryItem getInventoryItemEntity(String productId) {
        String tenantId = TenantContext.requireTenantId();
        return inventoryItemRepository
                .findByTenantIdAndProductId(tenantId, productId)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory not found for product: " + productId));
    }
}
