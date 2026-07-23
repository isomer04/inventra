package com.inventra.api.category;

import com.inventra.api.category.dto.CategoryResponse;
import com.inventra.api.category.dto.CreateCategoryRequest;
import com.inventra.api.category.dto.UpdateCategoryRequest;
import com.inventra.api.entity.Category;
import com.inventra.api.entity.Tenant;
import com.inventra.api.exception.ResourceInUseException;
import com.inventra.api.exception.ResourceNotFoundException;
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

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private CategoryMapper categoryMapper;

    @InjectMocks
    private CategoryService categoryService;

    private static final String TENANT_ID = "tenant-123";
    private static final String CATEGORY_ID = "category-123";
    private static final String PARENT_ID = "parent-123";

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(TENANT_ID);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void create_whenValidRequest_thenCreatesCategory() {
        CreateCategoryRequest request = new CreateCategoryRequest("Electronics", null);

        Tenant tenant = Tenant.builder()
                .id(TENANT_ID)
                .build();

        Category savedCategory = Category.builder()
                .id(CATEGORY_ID)
                .tenant(tenant)
                .name("Electronics")
                .build();

        CategoryResponse expectedResponse = new CategoryResponse(CATEGORY_ID, "Electronics", null, null);

        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(categoryRepository.save(any(Category.class))).thenReturn(savedCategory);
        when(categoryMapper.toResponse(savedCategory)).thenReturn(expectedResponse);

        CategoryResponse result = categoryService.create(request);

        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(CATEGORY_ID);

        verify(categoryRepository).save(any(Category.class));
    }

    @Test
    void create_whenWithParent_thenCreatesSubcategory() {
        CreateCategoryRequest request = new CreateCategoryRequest("Laptops", PARENT_ID);

        Tenant tenant = Tenant.builder().id(TENANT_ID).build();
        Category parent = Category.builder()
                .id(PARENT_ID)
                .tenant(tenant)
                .name("Electronics")
                .build();

        Category savedCategory = Category.builder()
                .id(CATEGORY_ID)
                .tenant(tenant)
                .name("Laptops")
                .parent(parent)
                .build();

        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(categoryRepository.findByIdAndTenantId(PARENT_ID, TENANT_ID)).thenReturn(Optional.of(parent));
        when(categoryRepository.save(any(Category.class))).thenReturn(savedCategory);
        when(categoryMapper.toResponse(any())).thenReturn(new CategoryResponse(CATEGORY_ID, "Laptops", PARENT_ID, null));

        categoryService.create(request);

        verify(categoryRepository).findByIdAndTenantId(PARENT_ID, TENANT_ID);
        verify(categoryRepository).save(any(Category.class));
    }

    @Test
    void create_whenParentNotFound_thenThrowsResourceNotFoundException() {
        CreateCategoryRequest request = new CreateCategoryRequest("Laptops", PARENT_ID);

        Tenant tenant = Tenant.builder().id(TENANT_ID).build();

        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(categoryRepository.findByIdAndTenantId(PARENT_ID, TENANT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> categoryService.create(request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Parent category not found");

        verify(categoryRepository, never()).save(any());
    }

    @Test
    void update_whenValidRequest_thenUpdatesCategory() {
        UpdateCategoryRequest request = new UpdateCategoryRequest("Updated Name", null);

        Category category = Category.builder()
                .id(CATEGORY_ID)
                .tenant(Tenant.builder().id(TENANT_ID).build())
                .name("Old Name")
                .build();

        when(categoryRepository.findByIdAndTenantId(CATEGORY_ID, TENANT_ID)).thenReturn(Optional.of(category));
        when(categoryRepository.save(category)).thenReturn(category);
        when(categoryMapper.toResponse(any())).thenReturn(new CategoryResponse(CATEGORY_ID, "Updated Name", null, null));

        categoryService.update(CATEGORY_ID, request);

        assertThat(category.getName()).isEqualTo("Updated Name");
        assertThat(category.getParent()).isNull();

        verify(categoryRepository).save(category);
    }

    @Test
    void update_whenSettingSelfAsParent_thenThrowsIllegalArgumentException() {
        UpdateCategoryRequest request = new UpdateCategoryRequest("Name", CATEGORY_ID);

        Category category = Category.builder()
                .id(CATEGORY_ID)
                .tenant(Tenant.builder().id(TENANT_ID).build())
                .build();

        when(categoryRepository.findByIdAndTenantId(CATEGORY_ID, TENANT_ID)).thenReturn(Optional.of(category));

        assertThatThrownBy(() -> categoryService.update(CATEGORY_ID, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Category cannot be its own parent");

        verify(categoryRepository, never()).save(any());
    }

    @Test
    void update_whenCategoryNotFound_thenThrowsResourceNotFoundException() {
        UpdateCategoryRequest request = new UpdateCategoryRequest("Name", null);

        when(categoryRepository.findByIdAndTenantId(CATEGORY_ID, TENANT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> categoryService.update(CATEGORY_ID, request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Category not found");
    }

    @Test
    void delete_whenNoChildrenOrProducts_thenDeletesCategory() {
        Category category = Category.builder()
                .id(CATEGORY_ID)
                .tenant(Tenant.builder().id(TENANT_ID).build())
                .build();

        when(categoryRepository.findByIdAndTenantId(CATEGORY_ID, TENANT_ID)).thenReturn(Optional.of(category));
        when(categoryRepository.countByParentIdAndTenantId(CATEGORY_ID, TENANT_ID)).thenReturn(0L);
        when(productRepository.countByCategoryIdAndTenantId(CATEGORY_ID, TENANT_ID)).thenReturn(0L);

        categoryService.delete(CATEGORY_ID);

        verify(categoryRepository).delete(category);
    }

    @Test
    void delete_whenHasSubcategories_thenThrowsResourceInUseException() {
        Category category = Category.builder()
                .id(CATEGORY_ID)
                .tenant(Tenant.builder().id(TENANT_ID).build())
                .build();

        when(categoryRepository.findByIdAndTenantId(CATEGORY_ID, TENANT_ID)).thenReturn(Optional.of(category));
        when(categoryRepository.countByParentIdAndTenantId(CATEGORY_ID, TENANT_ID)).thenReturn(3L);

        assertThatThrownBy(() -> categoryService.delete(CATEGORY_ID))
                .isInstanceOf(ResourceInUseException.class)
                .hasMessageContaining("3 subcategory(s) are linked to it");

        verify(categoryRepository, never()).delete(any());
    }

    @Test
    void delete_whenHasProducts_thenThrowsResourceInUseException() {
        Category category = Category.builder()
                .id(CATEGORY_ID)
                .tenant(Tenant.builder().id(TENANT_ID).build())
                .build();

        when(categoryRepository.findByIdAndTenantId(CATEGORY_ID, TENANT_ID)).thenReturn(Optional.of(category));
        when(categoryRepository.countByParentIdAndTenantId(CATEGORY_ID, TENANT_ID)).thenReturn(0L);
        when(productRepository.countByCategoryIdAndTenantId(CATEGORY_ID, TENANT_ID)).thenReturn(5L);

        assertThatThrownBy(() -> categoryService.delete(CATEGORY_ID))
                .isInstanceOf(ResourceInUseException.class)
                .hasMessageContaining("5 product(s) are linked to it");

        verify(categoryRepository, never()).delete(any());
    }

    @Test
    void delete_whenCategoryNotFound_thenThrowsResourceNotFoundException() {
        when(categoryRepository.findByIdAndTenantId(CATEGORY_ID, TENANT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> categoryService.delete(CATEGORY_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Category not found");

        verify(categoryRepository, never()).delete(any());
    }

    @Test
    void getById_whenCategoryExists_thenReturnsCategory() {
        Category category = Category.builder()
                .id(CATEGORY_ID)
                .tenant(Tenant.builder().id(TENANT_ID).build())
                .build();

        CategoryResponse expectedResponse = new CategoryResponse(CATEGORY_ID, "Test Category", null, null);

        when(categoryRepository.findByIdAndTenantId(CATEGORY_ID, TENANT_ID)).thenReturn(Optional.of(category));
        when(categoryMapper.toResponse(category)).thenReturn(expectedResponse);

        CategoryResponse result = categoryService.getById(CATEGORY_ID);

        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(CATEGORY_ID);
    }

    @Test
    void getById_whenCategoryNotFound_thenThrowsResourceNotFoundException() {
        when(categoryRepository.findByIdAndTenantId(CATEGORY_ID, TENANT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> categoryService.getById(CATEGORY_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Category not found");
    }
}
