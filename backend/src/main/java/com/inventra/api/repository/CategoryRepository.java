package com.inventra.api.repository;

import com.inventra.api.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CategoryRepository extends JpaRepository<Category, String> {

    List<Category> findAllByTenantId(String tenantId);

    Optional<Category> findByIdAndTenantId(String id, String tenantId);

    boolean existsByIdAndTenantId(String id, String tenantId);

    long countByParentIdAndTenantId(String parentId, String tenantId);
}
