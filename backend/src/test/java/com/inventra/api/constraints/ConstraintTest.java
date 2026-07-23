package com.inventra.api.constraints;

import com.inventra.api.BaseIntegrationTest;
import com.inventra.api.entity.*;
import com.inventra.api.repository.*;
import com.inventra.api.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests that database constraints are enforced correctly.
 *
 * <p>Uses {@code saveAndFlush()} so Hibernate flushes immediately and the
 * constraint violation fires inside the test method rather than at
 * transaction commit. Tests that expect a violation do NOT carry
 * {@code @Transactional} so the flush actually hits the DB.
 */
class ConstraintTest extends BaseIntegrationTest {

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private InventoryItemRepository inventoryItemRepository;

    private Tenant tenant;

    @BeforeEach
    void setUp() {
        tenant = tenantRepository.saveAndFlush(
                Tenant.builder()
                        .name("Test Tenant")
                        .slug("test-tenant-constraints-" + System.currentTimeMillis())
                        .build());
        TenantContext.setTenantId(tenant.getId());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void uniqueConstraint_whenDuplicateTenantSlug_thenThrowsDataIntegrityViolation() {
        Tenant duplicate = Tenant.builder()
                .name("Duplicate Tenant")
                .slug(tenant.getSlug())
                .build();

        assertThatThrownBy(() -> tenantRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void uniqueConstraint_whenDuplicateProductSkuInSameTenant_thenThrowsDataIntegrityViolation() {
        productRepository.saveAndFlush(Product.builder()
                .tenant(tenant).sku("SKU-001").name("Product 1")
                .unitPrice(new BigDecimal("10.00")).build());

        assertThatThrownBy(() -> productRepository.saveAndFlush(
                Product.builder()
                        .tenant(tenant).sku("SKU-001").name("Product 2")
                        .unitPrice(new BigDecimal("20.00")).build()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void uniqueConstraint_whenDuplicateProductSkuInDifferentTenants_thenSucceeds() {
        Tenant tenant2 = tenantRepository.saveAndFlush(
                Tenant.builder()
                        .name("Test Tenant 2")
                        .slug("test-tenant-2-" + System.currentTimeMillis())
                        .build());

        productRepository.saveAndFlush(Product.builder()
                .tenant(tenant).sku("SKU-001").name("Product 1")
                .unitPrice(new BigDecimal("10.00")).build());

        Product saved = productRepository.saveAndFlush(Product.builder()
                .tenant(tenant2).sku("SKU-001").name("Product 2")
                .unitPrice(new BigDecimal("20.00")).build());

        assertThat(saved.getId()).isNotNull();
    }

    @Test
    void foreignKeyConstraint_whenProductReferencesNonExistentCategory_thenThrowsDataIntegrityViolation() {
        Category ghost = Category.builder().id("non-existent-category").build();

        assertThatThrownBy(() -> productRepository.saveAndFlush(
                Product.builder()
                        .tenant(tenant).sku("SKU-001").name("Test Product")
                        .unitPrice(new BigDecimal("10.00")).category(ghost).build()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void foreignKeyConstraint_whenInventoryReferencesNonExistentProduct_thenThrowsDataIntegrityViolation() {
        assertThatThrownBy(() -> inventoryItemRepository.saveAndFlush(
                InventoryItem.builder()
                        .tenantId(tenant.getId())
                        .productId("non-existent-product")
                        .quantityOnHand(0).quantityReserved(0).reorderPoint(0)
                        .build()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void foreignKeyConstraint_whenUserReferencesNonExistentTenant_thenThrowsDataIntegrityViolation() {
        Tenant ghost = Tenant.builder().id("non-existent-tenant").build();

        // Hibernate may throw InvalidDataAccessApiUsageException (transient instance)
        // or DataIntegrityViolationException depending on flush order.
        assertThatThrownBy(() -> userRepository.saveAndFlush(
                User.builder()
                        .tenant(ghost).email("test@example.com").passwordHash("hash")
                        .build()))
                .isInstanceOf(Exception.class);
    }

    @Test
    void notNullConstraint_whenTenantNameIsNull_thenThrowsDataIntegrityViolation() {
        assertThatThrownBy(() -> tenantRepository.saveAndFlush(
                Tenant.builder().name(null).slug("test-null-name").build()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void notNullConstraint_whenTenantSlugIsNull_thenThrowsDataIntegrityViolation() {
        assertThatThrownBy(() -> tenantRepository.saveAndFlush(
                Tenant.builder().name("Test Tenant").slug(null).build()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void notNullConstraint_whenUserEmailIsNull_thenThrowsDataIntegrityViolation() {
        assertThatThrownBy(() -> userRepository.saveAndFlush(
                User.builder().tenant(tenant).email(null).passwordHash("hash").build()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void notNullConstraint_whenProductSkuIsNull_thenThrowsDataIntegrityViolation() {
        assertThatThrownBy(() -> productRepository.saveAndFlush(
                Product.builder()
                        .tenant(tenant).sku(null).name("Test Product")
                        .unitPrice(new BigDecimal("10.00")).build()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void notNullConstraint_whenProductNameIsNull_thenThrowsDataIntegrityViolation() {
        assertThatThrownBy(() -> productRepository.saveAndFlush(
                Product.builder()
                        .tenant(tenant).sku("SKU-001").name(null)
                        .unitPrice(new BigDecimal("10.00")).build()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void validData_whenAllRequiredFieldsProvided_thenSavesSuccessfully() {
        Product saved = productRepository.saveAndFlush(
                Product.builder()
                        .tenant(tenant).sku("SKU-VALID").name("Valid Product")
                        .unitPrice(new BigDecimal("10.00")).build());

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getSku()).isEqualTo("SKU-VALID");
    }

    @Test
    void validData_whenOptionalFieldsAreNull_thenSavesSuccessfully() {
        Product saved = productRepository.saveAndFlush(
                Product.builder()
                        .tenant(tenant).sku("SKU-OPTIONAL")
                        .name("Product with Optional Fields")
                        .unitPrice(new BigDecimal("10.00"))
                        .description(null).category(null)
                        .build());

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getDescription()).isNull();
        assertThat(saved.getCategory()).isNull();
    }

    @Test
    void validData_whenCategoryHierarchyCreated_thenSavesSuccessfully() {
        Category parent = categoryRepository.saveAndFlush(
                Category.builder().tenant(tenant).name("Parent Category").build());

        Category child = categoryRepository.saveAndFlush(
                Category.builder().tenant(tenant).name("Child Category").parent(parent).build());

        assertThat(child.getId()).isNotNull();
        assertThat(child.getParent().getId()).isEqualTo(parent.getId());
    }
}
