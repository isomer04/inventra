package com.inventra.api.product;

import com.inventra.api.entity.Category;
import com.inventra.api.entity.Product;
import com.inventra.api.entity.ProductStatus;
import com.inventra.api.entity.Tenant;
import com.inventra.api.exception.DuplicateResourceException;
import com.inventra.api.exception.ResourceNotFoundException;
import com.inventra.api.product.dto.CreateProductRequest;
import com.inventra.api.product.dto.ProductResponse;
import com.inventra.api.product.dto.UpdateProductRequest;
import com.inventra.api.repository.CategoryRepository;
import com.inventra.api.repository.ProductRepository;
import com.inventra.api.repository.TenantRepository;
import com.inventra.api.tenant.TenantContext;
import com.inventra.api.util.LogSanitizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final TenantRepository tenantRepository;
    private final ProductMapper productMapper;
    private final com.inventra.api.inventory.InventoryService inventoryService;

    @Transactional(readOnly = true)
    public Page<ProductResponse> getAll(String categoryId, ProductStatus status, String search, Pageable pageable) {
        return productRepository.findWithFilters(
                        TenantContext.requireTenantId(),
                        categoryId,
                        status,
                        search,
                        pageable)
                .map(productMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public ProductResponse getById(String id) {
        Product product = loadInTenant(id);
        return productMapper.toResponse(product);
    }

    public ProductResponse create(CreateProductRequest req) {
        String tenantId = TenantContext.requireTenantId();

        if (productRepository.existsByTenantIdAndSku(tenantId, req.sku())) {
            throw new DuplicateResourceException("SKU already exists: " + req.sku());
        }

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found"));

        Category category = null;
        if (req.categoryId() != null) {
            category = categoryRepository.findByIdAndTenantId(req.categoryId(), tenantId)
                    .orElseThrow(() -> new ResourceNotFoundException("Category not found: " + req.categoryId()));
        }

        Product product = productRepository.save(Product.builder()
                .tenant(tenant)
                .sku(req.sku())
                .name(req.name())
                .description(req.description())
                .category(category)
                .unitPrice(req.unitPrice())
                .unitOfMeasure(req.unitOfMeasure())
                .build());

        log.info("Created product [{}] with SKU [{}] in tenant [{}]", LogSanitizer.sanitize(product.getId()), LogSanitizer.sanitize(product.getSku()), LogSanitizer.sanitize(tenantId));
        
        inventoryService.createInventoryForProduct(product);
        
        return productMapper.toResponse(product);
    }

    public ProductResponse update(String id, UpdateProductRequest req) {
        Product product = loadInTenant(id);
        String tenantId = TenantContext.requireTenantId();

        if (req.sku() != null && !req.sku().equals(product.getSku())) {
            if (productRepository.existsByTenantIdAndSkuAndIdNot(tenantId, req.sku(), id)) {
                throw new DuplicateResourceException("SKU already exists: " + req.sku());
            }
            product.setSku(req.sku());
        }

        if (req.name() != null) product.setName(req.name());
        if (req.description() != null) product.setDescription(req.description());
        if (req.unitPrice() != null) product.setUnitPrice(req.unitPrice());
        if (req.unitOfMeasure() != null) product.setUnitOfMeasure(req.unitOfMeasure());
        if (req.status() != null) product.setStatus(req.status());

        if (req.categoryId() != null) {
            Category category = categoryRepository.findByIdAndTenantId(req.categoryId(), tenantId)
                    .orElseThrow(() -> new ResourceNotFoundException("Category not found: " + req.categoryId()));
            product.setCategory(category);
        }

        return productMapper.toResponse(productRepository.save(product));
    }

    public void delete(String id) {
        Product product = loadInTenant(id);
        product.setStatus(ProductStatus.DISCONTINUED);
        productRepository.save(product);
        log.info("Soft deleted product [{}] (status → DISCONTINUED)", id);
    }

    private Product loadInTenant(String id) {
        return productRepository.findByIdAndTenantId(id, TenantContext.requireTenantId())
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + id));
    }
    
    /**
     * Returns the entity itself (not a response DTO) for internal use by other services.
     */
    public Product getProductEntity(String id) {
        return loadInTenant(id);
    }
}
