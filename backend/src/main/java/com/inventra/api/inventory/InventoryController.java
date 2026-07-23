package com.inventra.api.inventory;

import com.inventra.api.entity.MovementType;
import com.inventra.api.inventory.dto.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/inventory")
@RequiredArgsConstructor
@Tag(name = "Inventory", description = "Inventory management endpoints")
@SecurityRequirement(name = "bearerAuth")
public class InventoryController {

    private final InventoryService inventoryService;
    private final StockMovementService stockMovementService;

    @GetMapping
    // Explicit annotation — all authenticated roles may view inventory.
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'WAREHOUSE_STAFF', 'VIEWER')")
    @Operation(summary = "List all inventory items", description = "Get paginated list of all inventory items for the authenticated tenant")
    public ResponseEntity<Page<InventoryItemResponse>> getAllInventory(
            @PageableDefault(size = 20, sort = "lastUpdated", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(inventoryService.getAllInventory(pageable));
    }

    @GetMapping("/{productId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'WAREHOUSE_STAFF', 'VIEWER')")
    @Operation(summary = "Get inventory by product ID", description = "Get inventory details for a specific product")
    public ResponseEntity<InventoryItemResponse> getInventoryByProductId(@PathVariable String productId) {
        return ResponseEntity.ok(inventoryService.getInventoryByProductId(productId));
    }

    @GetMapping("/low-stock")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'WAREHOUSE_STAFF', 'VIEWER')")
    @Operation(summary = "Get low stock items", description = "Get list of items at or below reorder point")
    public ResponseEntity<List<InventoryItemResponse>> getLowStockItems() {
        return ResponseEntity.ok(inventoryService.getLowStockItems());
    }

    @PutMapping("/{productId}/receive")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'WAREHOUSE_STAFF')")
    @Operation(summary = "Receive stock", description = "Receive stock for a product (creates RECEIPT movement)")
    public ResponseEntity<StockMovementResponse> receiveStock(
            @PathVariable String productId,
            @Valid @RequestBody ReceiveStockRequest request,
            @AuthenticationPrincipal com.inventra.api.entity.User currentUser) {

        StockMovementResponse response = stockMovementService.receiveStock(
                productId, request.getQuantity(), request.getNotes(), currentUser.getId());

        return ResponseEntity.ok(response);
    }

    @PutMapping("/{productId}/adjust")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Operation(summary = "Adjust stock", description = "Manually adjust stock quantity (creates ADJUSTMENT movement)")
    public ResponseEntity<StockMovementResponse> adjustStock(
            @PathVariable String productId,
            @Valid @RequestBody AdjustStockRequest request,
            @AuthenticationPrincipal com.inventra.api.entity.User currentUser) {

        StockMovementResponse response = stockMovementService.adjustStock(
                productId, request.getQuantity(), request.getNotes(), currentUser.getId());

        return ResponseEntity.ok(response);
    }

    @PutMapping("/{productId}/reorder-point")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Operation(summary = "Update reorder point", description = "Update the reorder point threshold for a product")
    public ResponseEntity<InventoryItemResponse> updateReorderPoint(
            @PathVariable String productId,
            @Valid @RequestBody UpdateReorderPointRequest request) {
        
        return ResponseEntity.ok(inventoryService.updateReorderPoint(productId, request.getReorderPoint()));
    }

    @GetMapping("/movements")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Operation(summary = "List stock movements", description = "Get paginated list of stock movements with optional filters")
    public ResponseEntity<Page<StockMovementResponse>> getMovements(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            @RequestParam(required = false) String productId,
            @RequestParam(required = false) MovementType type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) java.time.Instant startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) java.time.Instant endDate) {
        
        return ResponseEntity.ok(stockMovementService.getMovements(pageable, productId, type, startDate, endDate));
    }

    @GetMapping("/movements/{productId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Operation(summary = "List movements for product", description = "Get paginated list of stock movements for a specific product")
    public ResponseEntity<Page<StockMovementResponse>> getMovementsByProductId(
            @PathVariable String productId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        
        return ResponseEntity.ok(stockMovementService.getMovementsByProductId(productId, pageable));
    }
}
