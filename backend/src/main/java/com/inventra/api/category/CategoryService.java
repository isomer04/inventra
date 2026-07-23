package com.inventra.api.category;

import com.inventra.api.category.dto.CategoryResponse;
import com.inventra.api.category.dto.CreateCategoryRequest;
import com.inventra.api.category.dto.UpdateCategoryRequest;
import com.inventra.api.entity.Category;
import com.inventra.api.entity.Tenant;
import com.inventra.api.exception.DuplicateResourceException;
import com.inventra.api.exception.ResourceInUseException;
import com.inventra.api.exception.ResourceNotFoundException;
import com.inventra.api.repository.CategoryRepository;
import com.inventra.api.repository.ProductRepository;
import com.inventra.api.repository.TenantRepository;
import com.inventra.api.tenant.TenantContext;
import com.inventra.api.util.LogSanitizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final TenantRepository tenantRepository;
    private final CategoryMapper categoryMapper;

    @Transactional(readOnly = true)
    public List<CategoryResponse> getAll() {
        return categoryRepository.findAllByTenantId(TenantContext.requireTenantId())
                .stream()
                .map(categoryMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public CategoryResponse getById(String id) {
        Category category = loadInTenant(id);
        return categoryMapper.toResponse(category);
    }

    public CategoryResponse create(CreateCategoryRequest req) {
        Tenant tenant = tenantRepository.findById(TenantContext.requireTenantId())
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found"));

        Category parent = null;
        if (req.parentId() != null) {
            parent = categoryRepository.findByIdAndTenantId(req.parentId(), TenantContext.requireTenantId())
                    .orElseThrow(() -> new ResourceNotFoundException("Parent category not found: " + req.parentId()));
        }

        Category category = categoryRepository.save(Category.builder()
                .tenant(tenant)
                .name(req.name())
                .parent(parent)
                .build());

        log.info("Created category [{}] in tenant [{}]", LogSanitizer.sanitize(category.getId()), LogSanitizer.sanitize(tenant.getId()));
        return categoryMapper.toResponse(category);
    }

    public CategoryResponse update(String id, UpdateCategoryRequest req) {
        Category category = loadInTenant(id);

        category.setName(req.name());

        if (req.parentId() != null) {
            if (req.parentId().equals(id)) {
                throw new IllegalArgumentException("Category cannot be its own parent");
            }
            Category parent = categoryRepository.findByIdAndTenantId(req.parentId(), TenantContext.requireTenantId())
                    .orElseThrow(() -> new ResourceNotFoundException("Parent category not found: " + req.parentId()));
            category.setParent(parent);
        } else {
            category.setParent(null);
        }

        return categoryMapper.toResponse(categoryRepository.save(category));
    }

    public void delete(String id) {
        Category category = loadInTenant(id);
        String tenantId = TenantContext.requireTenantId();

        long childCount = categoryRepository.countByParentIdAndTenantId(id, tenantId);
        if (childCount > 0) {
            // ResourceInUseException is the correct exception for
            // referential-integrity blocks. DuplicateResourceException (409 Conflict)
            // was semantically correct by accident (same HTTP status) but misleading.
            throw new ResourceInUseException(
                    "Cannot delete category: " + childCount + " subcategory(s) are linked to it");
        }

        long productCount = productRepository.countByCategoryIdAndTenantId(id, tenantId);
        if (productCount > 0) {
            throw new ResourceInUseException(
                    "Cannot delete category: " + productCount + " product(s) are linked to it");
        }

        categoryRepository.delete(category);
        log.info("Deleted category [{}]", LogSanitizer.sanitize(id));
    }

    private Category loadInTenant(String id) {
        return categoryRepository.findByIdAndTenantId(id, TenantContext.requireTenantId())
                .orElseThrow(() -> new ResourceNotFoundException("Category not found: " + id));
    }
}
