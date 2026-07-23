package com.inventra.api.repository;

import com.inventra.api.entity.Product;
import com.inventra.api.entity.ProductStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, String> {

    Page<Product> findAllByTenantId(String tenantId, Pageable pageable);

    Optional<Product> findByIdAndTenantId(String id, String tenantId);

    boolean existsByTenantIdAndSku(String tenantId, String sku);

    boolean existsByTenantIdAndSkuAndIdNot(String tenantId, String sku, String id);

    @Query("SELECT p FROM Product p LEFT JOIN FETCH p.category WHERE p.tenant.id = :tenantId " +
           "AND (:categoryId IS NULL OR p.category.id = :categoryId) " +
           "AND (:status IS NULL OR p.status = :status) " +
           "AND (:search IS NULL OR LOWER(p.name) LIKE LOWER(CONCAT('%', :search, '%')) " +
           "     OR LOWER(p.sku) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<Product> findWithFilters(@Param("tenantId") String tenantId,
                                   @Param("categoryId") String categoryId,
                                   @Param("status") ProductStatus status,
                                   @Param("search") String search,
                                   Pageable pageable);

    long countByCategoryIdAndTenantId(String categoryId, String tenantId);
}
