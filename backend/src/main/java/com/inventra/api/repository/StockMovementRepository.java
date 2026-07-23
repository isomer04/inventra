package com.inventra.api.repository;

import com.inventra.api.entity.MovementType;
import com.inventra.api.entity.StockMovement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;

@Repository
public interface StockMovementRepository extends JpaRepository<StockMovement, String> {

    /**
     * Whether any stock movement was recorded by this user.
     *
     * <p>{@code stock_movement.created_by} is {@code ON DELETE RESTRICT} (V9 migration).
     * See {@code OrderRepository.existsByCreatedById} for the rationale.
     */
    boolean existsByCreatedBy(String userId);

    @Query(value = "SELECT m FROM StockMovement m LEFT JOIN FETCH m.product LEFT JOIN FETCH m.creator WHERE m.tenantId = :tenantId",
           countQuery = "SELECT COUNT(m) FROM StockMovement m WHERE m.tenantId = :tenantId")
    Page<StockMovement> findByTenantId(String tenantId, Pageable pageable);

    @Query(value = "SELECT m FROM StockMovement m LEFT JOIN FETCH m.product LEFT JOIN FETCH m.creator WHERE m.tenantId = :tenantId AND m.productId = :productId",
           countQuery = "SELECT COUNT(m) FROM StockMovement m WHERE m.tenantId = :tenantId AND m.productId = :productId")
    Page<StockMovement> findByTenantIdAndProductId(String tenantId, String productId, Pageable pageable);

    @Query("SELECT m FROM StockMovement m LEFT JOIN FETCH m.product LEFT JOIN FETCH m.creator " +
           "WHERE m.tenantId = :tenantId " +
           "AND (:productId IS NULL OR m.productId = :productId) " +
           "AND (:type IS NULL OR m.type = :type) " +
           "AND (:startDate IS NULL OR m.createdAt >= :startDate) " +
           "AND (:endDate IS NULL OR m.createdAt <= :endDate)")
    Page<StockMovement> findByFilters(
        @Param("tenantId") String tenantId,
        @Param("productId") String productId,
        @Param("type") MovementType type,
        @Param("startDate") Instant startDate,
        @Param("endDate") Instant endDate,
        Pageable pageable
    );
}
