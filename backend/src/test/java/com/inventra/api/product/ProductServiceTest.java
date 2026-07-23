package com.inventra.api.product;

import com.inventra.api.entity.Category;
import com.inventra.api.entity.Product;
import com.inventra.api.entity.ProductStatus;
import com.inventra.api.entity.Tenant;
import com.inventra.api.exception.DuplicateResourceException;
import com.inventra.api.exception.ResourceNotFoundException;
import com.inventra.api.inventory.InventoryService;
import com.inventra.api.product.dto.CreateProductRequest;
import com.inventra.api.product.dto.ProductResponse;
import com.inventra.api.product.dto.UpdateProductRequest;
import com.inventra.api.repository.CategoryRepository;
import com.inventra.api.repository.ProductRepository;
import com.inventra.api.repository.TenantRepository;
import com.inventra.api.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private ProductMapper productMapper;

    @Mock
    private InventoryService inventoryService;

    @InjectMocks
    private ProductService productService;

    private static final String TENANT_ID = "tenant-123";
    private static final String PRODUCT_ID = "product-123";
    private static final String CATEGORY_ID = "category-123";
    private static final String SKU = "SKU-001";

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(TENANT_ID);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void create_whenValidRequest_thenCreatesProductAndInventory() {
        CreateProductRequest request = new CreateProductRequest(
                SKU,
                "Test Product",
                "Description",
                CATEGORY_ID,
                new BigDecimal("10.00"),
                "EA"
        );

        Tenant tenant = Tenant.builder()
                .id(TENANT_ID)
                .build();

        Category category = Category.builder()
                .id(CATEGORY_ID)
                .tenant(Tenant.builder().id(TENANT_ID).build())
                .build();

        Product savedProduct = Product.builder()
                .id(PRODUCT_ID)
                .tenant(tenant)
                .sku(SKU)
                .name("Test Product")
                .category(category)
                .build();

        ProductResponse expectedResponse = new ProductResponse(
                PRODUCT_ID, SKU, "Test Product", "Description", CATEGORY_ID, "Electronics",
                new BigDecimal("10.00"), "EA", ProductStatus.ACTIVE, null, null
        );

        when(productRepository.existsByTenantIdAndSku(TENANT_ID, SKU)).thenReturn(false);
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(categoryRepository.findByIdAndTenantId(CATEGORY_ID, TENANT_ID)).thenReturn(Optional.of(category));
        when(productRepository.save(any(Product.class))).thenReturn(savedProduct);
        when(productMapper.toResponse(savedProduct)).thenReturn(expectedResponse);

        ProductResponse result = productService.create(request);

        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(PRODUCT_ID);

        verify(productRepository).save(any(Product.class));
        verify(inventoryService).createInventoryForProduct(savedProduct);
    }

    @Test
    void create_whenSkuAlreadyExists_thenThrowsDuplicateResourceException() {
        CreateProductRequest request = new CreateProductRequest(
                SKU,
                "Test Product",
                null,
                null,
                new BigDecimal("10.00"),
                "EA"
        );

        when(productRepository.existsByTenantIdAndSku(TENANT_ID, SKU)).thenReturn(true);

        assertThatThrownBy(() -> productService.create(request))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("SKU already exists");

        verify(productRepository, never()).save(any());
        verify(inventoryService, never()).createInventoryForProduct(any());
    }

    @Test
    void create_whenCategoryNotFound_thenThrowsResourceNotFoundException() {
        CreateProductRequest request = new CreateProductRequest(
                SKU,
                "Test Product",
                null,
                CATEGORY_ID,
                new BigDecimal("10.00"),
                "EA"
        );

        Tenant tenant = Tenant.builder().id(TENANT_ID).build();

        when(productRepository.existsByTenantIdAndSku(TENANT_ID, SKU)).thenReturn(false);
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(categoryRepository.findByIdAndTenantId(CATEGORY_ID, TENANT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.create(request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Category not found");

        verify(productRepository, never()).save(any());
    }

    @Test
    void create_whenNoCategoryProvided_thenCreatesProductWithoutCategory() {
        CreateProductRequest request = new CreateProductRequest(
                SKU,
                "Test Product",
                null,
                null, // No category
                new BigDecimal("10.00"),
                "EA"
        );

        Tenant tenant = Tenant.builder().id(TENANT_ID).build();
        Product savedProduct = Product.builder()
                .id(PRODUCT_ID)
                .tenant(tenant)
                .sku(SKU)
                .category(null)
                .build();

        when(productRepository.existsByTenantIdAndSku(TENANT_ID, SKU)).thenReturn(false);
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(productRepository.save(any(Product.class))).thenReturn(savedProduct);
        when(productMapper.toResponse(any())).thenReturn(new ProductResponse(
                PRODUCT_ID, SKU, "Test Product", null, null, null,
                new BigDecimal("10.00"), "EA", ProductStatus.ACTIVE, null, null
        ));

        productService.create(request);

        verify(categoryRepository, never()).findByIdAndTenantId(anyString(), anyString());
        verify(productRepository).save(any(Product.class));
    }

    @Test
    void update_whenValidRequest_thenUpdatesProduct() {
        UpdateProductRequest request = new UpdateProductRequest(
                null, // Don't change SKU
                "Updated Name",
                "Updated Description",
                null,
                new BigDecimal("15.00"),
                "KG",
                ProductStatus.ACTIVE
        );

        Product product = Product.builder()
                .id(PRODUCT_ID)
                .tenant(Tenant.builder().id(TENANT_ID).build())
                .sku(SKU)
                .name("Old Name")
                .build();

        Product updatedProduct = Product.builder()
                .id(PRODUCT_ID)
                .name("Updated Name")
                .build();

        when(productRepository.findByIdAndTenantId(PRODUCT_ID, TENANT_ID)).thenReturn(Optional.of(product));
        when(productRepository.save(product)).thenReturn(updatedProduct);
        when(productMapper.toResponse(updatedProduct)).thenReturn(new ProductResponse(
                PRODUCT_ID, SKU, "Updated Name", "Updated Description", null, null,
                new BigDecimal("15.00"), "KG", ProductStatus.ACTIVE, null, null
        ));

        productService.update(PRODUCT_ID, request);

        assertThat(product.getName()).isEqualTo("Updated Name");
        assertThat(product.getDescription()).isEqualTo("Updated Description");
        assertThat(product.getUnitPrice()).isEqualTo(new BigDecimal("15.00"));
        assertThat(product.getUnitOfMeasure()).isEqualTo("KG");
        assertThat(product.getStatus()).isEqualTo(ProductStatus.ACTIVE);

        verify(productRepository).save(product);
    }

    @Test
    void update_whenChangingSkuToDuplicate_thenThrowsDuplicateResourceException() {
        UpdateProductRequest request = new UpdateProductRequest(
                "NEW-SKU",
                null,
                null,
                null,
                null,
                null,
                null
        );

        Product product = Product.builder()
                .id(PRODUCT_ID)
                .tenant(Tenant.builder().id(TENANT_ID).build())
                .sku(SKU)
                .build();

        when(productRepository.findByIdAndTenantId(PRODUCT_ID, TENANT_ID)).thenReturn(Optional.of(product));
        when(productRepository.existsByTenantIdAndSkuAndIdNot(TENANT_ID, "NEW-SKU", PRODUCT_ID)).thenReturn(true);

        assertThatThrownBy(() -> productService.update(PRODUCT_ID, request))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("SKU already exists");

        verify(productRepository, never()).save(any());
    }

    @Test
    void update_whenProductNotFound_thenThrowsResourceNotFoundException() {
        UpdateProductRequest request = new UpdateProductRequest(
                null,
                "New Name",
                null,
                null,
                null,
                null,
                null
        );

        when(productRepository.findByIdAndTenantId(PRODUCT_ID, TENANT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.update(PRODUCT_ID, request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Product not found");
    }

    @Test
    void delete_whenProductExists_thenSoftDeletesBySettingStatusToDiscontinued() {
        Product product = Product.builder()
                .id(PRODUCT_ID)
                .tenant(Tenant.builder().id(TENANT_ID).build())
                .status(ProductStatus.ACTIVE)
                .build();

        when(productRepository.findByIdAndTenantId(PRODUCT_ID, TENANT_ID)).thenReturn(Optional.of(product));

        productService.delete(PRODUCT_ID);

        assertThat(product.getStatus()).isEqualTo(ProductStatus.DISCONTINUED);
        verify(productRepository).save(product);
        verify(productRepository, never()).delete(any());
    }

    @Test
    void delete_whenProductNotFound_thenThrowsResourceNotFoundException() {
        when(productRepository.findByIdAndTenantId(PRODUCT_ID, TENANT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.delete(PRODUCT_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Product not found");

        verify(productRepository, never()).save(any());
    }

    @Test
    void getById_whenProductExists_thenReturnsProduct() {
        Product product = Product.builder()
                .id(PRODUCT_ID)
                .tenant(Tenant.builder().id(TENANT_ID).build())
                .build();

        ProductResponse expectedResponse = new ProductResponse(
                PRODUCT_ID, "SKU-001", "Test Product", null, null, null,
                new BigDecimal("10.00"), "EA", ProductStatus.ACTIVE, null, null
        );

        when(productRepository.findByIdAndTenantId(PRODUCT_ID, TENANT_ID)).thenReturn(Optional.of(product));
        when(productMapper.toResponse(product)).thenReturn(expectedResponse);

        ProductResponse result = productService.getById(PRODUCT_ID);

        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(PRODUCT_ID);
    }

    @Test
    void getById_whenProductNotFound_thenThrowsResourceNotFoundException() {
        when(productRepository.findByIdAndTenantId(PRODUCT_ID, TENANT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.getById(PRODUCT_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Product not found");
    }

    @Test
    void getProductEntity_whenProductExists_thenReturnsEntity() {
        Product product = Product.builder()
                .id(PRODUCT_ID)
                .tenant(Tenant.builder().id(TENANT_ID).build())
                .build();

        when(productRepository.findByIdAndTenantId(PRODUCT_ID, TENANT_ID)).thenReturn(Optional.of(product));

        Product result = productService.getProductEntity(PRODUCT_ID);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(PRODUCT_ID);
    }
}
