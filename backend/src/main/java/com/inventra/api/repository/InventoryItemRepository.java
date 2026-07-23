package com.inventra.api.repository;

import com.inventra.api.entity.InventoryItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InventoryItemRepository extends JpaRepository<InventoryItem, String> {

    @Query(value = "SELECT i FROM InventoryItem i LEFT JOIN FETCH i.product WHERE i.tenantId = :tenantId",
           countQuery = "SELECT COUNT(i) FROM InventoryItem i WHERE i.tenantId = :tenantId")
    Page<InventoryItem> findByTenantId(@Param("tenantId") String tenantId, Pageable pageable);

    @Query("SELECT i FROM InventoryItem i LEFT JOIN FETCH i.product WHERE i.tenantId = :tenantId AND i.productId = :productId")
    Optional<InventoryItem> findByTenantIdAndProductId(@Param("tenantId") String tenantId, @Param("productId") String productId);

    boolean existsByTenantIdAndProductId(String tenantId, String productId);

    @Query("SELECT i FROM InventoryItem i LEFT JOIN FETCH i.product WHERE i.tenantId = :tenantId AND (i.quantityOnHand - i.quantityReserved) <= i.reorderPoint ORDER BY (i.quantityOnHand - i.quantityReserved) ASC")
    List<InventoryItem> findLowStockItems(@Param("tenantId") String tenantId);
}
