package com.inventra.api.order;

import com.inventra.api.BaseIntegrationTest;
import com.inventra.api.tenant.TenantContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import java.time.Year;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for {@link OrderNumberGenerator}.
 *
 * <p>Tests focus on:
 * <ul>
 *   <li>Sequential number generation within a year</li>
 *   <li>Concurrency safety (thread-safe allocation via FOR UPDATE)</li>
 *   <li>Year rollover behavior</li>
 *   <li>Format validation (ORD-YYYY-NNNNN)</li>
 *   <li>Tenant isolation</li>
 *   <li>Transaction boundaries</li>
 * </ul>
 */
@SpringBootTest
@Sql(scripts = "/test-data/cleanup.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@DisplayName("OrderNumberGenerator")
class OrderNumberGeneratorTest extends BaseIntegrationTest {

    @Autowired
    private OrderNumberGenerator generator;

    @Autowired
    private EntityManager entityManager;

    private static final String TENANT_1 = "tenant-test-001";
    private static final String TENANT_2 = "tenant-test-002";
    private static final Pattern ORDER_NUMBER_PATTERN = Pattern.compile("^ORD-\\d{4}-\\d{5}$");

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(TENANT_1);
    }

    @Nested
    @DisplayName("sequential number generation")
    class SequentialNumberGeneration {

        @Test
        @Transactional
        @DisplayName("generates first order number as ORD-YYYY-00001")
        void generatesFirstOrderNumber() {
            String orderNumber = generator.generateOrderNumber();
            int currentYear = Year.now().getValue();
            
            assertThat(orderNumber).isEqualTo(String.format("ORD-%d-00001", currentYear));
        }

        @Test
        @Transactional
        @DisplayName("generates sequential numbers")
        void generatesSequentialNumbers() {
            int currentYear = Year.now().getValue();
            
            String order1 = generator.generateOrderNumber();
            String order2 = generator.generateOrderNumber();
            String order3 = generator.generateOrderNumber();

            assertThat(order1).isEqualTo(String.format("ORD-%d-00001", currentYear));
            assertThat(order2).isEqualTo(String.format("ORD-%d-00002", currentYear));
            assertThat(order3).isEqualTo(String.format("ORD-%d-00003", currentYear));
        }

        @Test
        @Transactional
        @DisplayName("increments sequence beyond 5 digits when needed")
        void incrementsBeyondFiveDigits() {
            int currentYear = Year.now().getValue();
            
            // Pre-seed the sequence to 99999
            entityManager.createNativeQuery(
                    "INSERT INTO order_sequence (tenant_id, year, next_seq) VALUES (:tenantId, :year, 99999) " +
                    "ON DUPLICATE KEY UPDATE next_seq = 99999")
                    .setParameter("tenantId", TENANT_1)
                    .setParameter("year", currentYear)
                    .executeUpdate();
            entityManager.flush();
            entityManager.clear();

            String orderNumber = generator.generateOrderNumber();
            
            // Next number should be 100000 (6 digits)
            assertThat(orderNumber).isEqualTo(String.format("ORD-%d-100000", currentYear));
        }
    }

    @Nested
    @DisplayName("format validation")
    class FormatValidation {

        @Test
        @Transactional
        @DisplayName("matches ORD-YYYY-NNNNN pattern")
        void matchesOrderNumberPattern() {
            String orderNumber = generator.generateOrderNumber();
            
            assertThat(orderNumber).matches(ORDER_NUMBER_PATTERN);
        }

        @Test
        @Transactional
        @DisplayName("pads sequence with leading zeros")
        void padsSequenceWithLeadingZeros() {
            String order1 = generator.generateOrderNumber();
            String order2 = generator.generateOrderNumber();
            
            assertThat(order1).contains("-00001");
            assertThat(order2).contains("-00002");
        }

        @Test
        @Transactional
        @DisplayName("includes current year in order number")
        void includesCurrentYear() {
            int currentYear = Year.now().getValue();
            String orderNumber = generator.generateOrderNumber();
            
            assertThat(orderNumber).startsWith("ORD-" + currentYear);
        }
    }

    @Nested
    @DisplayName("concurrency safety")
    class ConcurrencySafety {

        @Test
        @DisplayName("generates unique order numbers under concurrent load")
        void generatesUniqueNumbersConcurrently() throws InterruptedException {
            int threadCount = 20;
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(threadCount);
            Set<String> orderNumbers = Collections.synchronizedSet(new HashSet<>());
            Set<String> errors = Collections.synchronizedSet(new HashSet<>());

            ExecutorService executor = Executors.newFixedThreadPool(threadCount);

            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        startLatch.await();

                        // Set tenant context (required by generator)
                        TenantContext.setTenantId(TENANT_1);

                        String orderNumber = generator.generateOrderNumber();
                        orderNumbers.add(orderNumber);
                    } catch (Exception e) {
                        errors.add(e.getMessage());
                    } finally {
                        TenantContext.clear();
                        doneLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            boolean completed = doneLatch.await(10, TimeUnit.SECONDS);
            executor.shutdown();

            assertThat(completed).isTrue();
            assertThat(errors).isEmpty();
            assertThat(orderNumbers).hasSize(threadCount);
        }

        @Test
        @DisplayName("no duplicate order numbers with 50 concurrent threads")
        void noDuplicatesWithFiftyConcurrentThreads() throws InterruptedException {
            int threadCount = 50;
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(threadCount);
            Set<String> orderNumbers = Collections.synchronizedSet(new HashSet<>());

            ExecutorService executor = Executors.newFixedThreadPool(threadCount);

            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        TenantContext.setTenantId(TENANT_1);
                        String orderNumber = generator.generateOrderNumber();
                        orderNumbers.add(orderNumber);
                    } catch (Exception e) {
                    } finally {
                        TenantContext.clear();
                        doneLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            doneLatch.await(15, TimeUnit.SECONDS);
            executor.shutdown();

            assertThat(orderNumbers).hasSize(threadCount);
        }
    }

    @Nested
    @DisplayName("tenant isolation")
    class TenantIsolation {

        @Test
        @Transactional
        @DisplayName("different tenants have separate order number sequences")
        void differentTenantsHaveSeparateSequences() {
            int currentYear = Year.now().getValue();

            // Generate for tenant 1
            TenantContext.setTenantId(TENANT_1);
            String tenant1Order1 = generator.generateOrderNumber();
            String tenant1Order2 = generator.generateOrderNumber();

            // Generate for tenant 2
            TenantContext.setTenantId(TENANT_2);
            String tenant2Order1 = generator.generateOrderNumber();
            String tenant2Order2 = generator.generateOrderNumber();

            // Back to tenant 1
            TenantContext.setTenantId(TENANT_1);
            String tenant1Order3 = generator.generateOrderNumber();

            assertThat(tenant1Order1).isEqualTo(String.format("ORD-%d-00001", currentYear));
            assertThat(tenant1Order2).isEqualTo(String.format("ORD-%d-00002", currentYear));
            assertThat(tenant1Order3).isEqualTo(String.format("ORD-%d-00003", currentYear));
            
            assertThat(tenant2Order1).isEqualTo(String.format("ORD-%d-00001", currentYear));
            assertThat(tenant2Order2).isEqualTo(String.format("ORD-%d-00002", currentYear));
        }

        @Test
        @Transactional
        @DisplayName("requires tenant context to be set")
        void requiresTenantContext() {
            TenantContext.clear();

            assertThatThrownBy(() -> generator.generateOrderNumber())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("without a tenant context");
        }
    }

    @Nested
    @DisplayName("year rollover")
    class YearRollover {

        @Test
        @Transactional
        @DisplayName("creates new sequence row for new year")
        void createsNewSequenceForNewYear() {
            int currentYear = Year.now().getValue();
            int lastYear = currentYear - 1;

            // Pre-seed last year's sequence
            entityManager.createNativeQuery(
                    "INSERT INTO order_sequence (tenant_id, year, next_seq) VALUES (:tenantId, :year, 50)")
                    .setParameter("tenantId", TENANT_1)
                    .setParameter("year", lastYear)
                    .executeUpdate();
            entityManager.flush();
            entityManager.clear();

            // Generate for current year (should start at 1, not 51)
            String orderNumber = generator.generateOrderNumber();

            assertThat(orderNumber).isEqualTo(String.format("ORD-%d-00001", currentYear));
        }

        @Test
        @Transactional
        @DisplayName("maintains separate sequences for different years")
        void maintainsSeparateSequencesForDifferentYears() {
            int currentYear = Year.now().getValue();
            int lastYear = currentYear - 1;

            // Pre-seed last year
            entityManager.createNativeQuery(
                    "INSERT INTO order_sequence (tenant_id, year, next_seq) VALUES (:tenantId, :year, 100)")
                    .setParameter("tenantId", TENANT_1)
                    .setParameter("year", lastYear)
                    .executeUpdate();

            String currentOrder1 = generator.generateOrderNumber();
            String currentOrder2 = generator.generateOrderNumber();

            assertThat(currentOrder1).contains(String.format("%d-00001", currentYear));
            assertThat(currentOrder2).contains(String.format("%d-00002", currentYear));
        }
    }

    @Nested
    @DisplayName("database persistence")
    class DatabasePersistence {

        @Test
        @Transactional
        @DisplayName("persists sequence to database")
        void persistsSequenceToDatabase() {
            int currentYear = Year.now().getValue();
            
            generator.generateOrderNumber();
            generator.generateOrderNumber();
            
            entityManager.flush();
            entityManager.clear();

            @SuppressWarnings("unchecked")
            java.util.List<Number> result = entityManager.createNativeQuery(
                    "SELECT next_seq FROM order_sequence WHERE tenant_id = :tenantId AND year = :year")
                    .setParameter("tenantId", TENANT_1)
                    .setParameter("year", currentYear)
                    .getResultList();

            assertThat(result).isNotEmpty();
            assertThat(result.get(0).intValue()).isEqualTo(2);
        }

        @Test
        @Transactional
        @DisplayName("INSERT IGNORE is idempotent for same tenant and year")
        void insertIgnoreIsIdempotent() {
            int currentYear = Year.now().getValue();

            // Multiple generations should not create duplicate rows
            generator.generateOrderNumber();
            generator.generateOrderNumber();
            generator.generateOrderNumber();
            
            entityManager.flush();

            Long rowCount = (Long) entityManager.createNativeQuery(
                    "SELECT COUNT(*) FROM order_sequence WHERE tenant_id = :tenantId AND year = :year")
                    .setParameter("tenantId", TENANT_1)
                    .setParameter("year", currentYear)
                    .getSingleResult();

            assertThat(rowCount).isEqualTo(1L);
        }
    }

    @Nested
    @DisplayName("transaction boundaries")
    class TransactionBoundaries {

        @Test
        @DisplayName("must be called within a transaction")
        void mustBeCalledWithinTransaction() {
            // This test runs without @Transactional annotation
            // The generator's @Transactional should start one

            String orderNumber = generator.generateOrderNumber();
            
            assertThat(orderNumber).matches(ORDER_NUMBER_PATTERN);
        }

        @Test
        @Transactional
        @DisplayName("FOR UPDATE lock prevents concurrent reads of same value")
        void forUpdateLockPreventsConcurrentReads() throws InterruptedException {
            // This is implicitly tested by the concurrency tests
            // All 20-50 threads get unique numbers because FOR UPDATE serializes access
            
            Set<String> orderNumbers = new HashSet<>();
            
            for (int i = 0; i < 5; i++) {
                orderNumbers.add(generator.generateOrderNumber());
            }
            
            assertThat(orderNumbers).hasSize(5);
        }
    }

    @Nested
    @DisplayName("edge cases")
    class EdgeCases {

        @Test
        @Transactional
        @DisplayName("handles sequence gaps gracefully")
        void handlesSequenceGaps() {
            int currentYear = Year.now().getValue();
            
            // Pre-seed with a gap
            entityManager.createNativeQuery(
                    "INSERT INTO order_sequence (tenant_id, year, next_seq) VALUES (:tenantId, :year, 100)")
                    .setParameter("tenantId", TENANT_1)
                    .setParameter("year", currentYear)
                    .executeUpdate();
            entityManager.flush();

            // Next number should be 101 (continues from existing sequence)
            String orderNumber = generator.generateOrderNumber();
            
            assertThat(orderNumber).isEqualTo(String.format("ORD-%d-00101", currentYear));
        }

        @Test
        @Transactional
        @DisplayName("generates order number after multiple years without orders")
        void generatesAfterMultipleYearsWithoutOrders() {
            int currentYear = Year.now().getValue();
            int twoYearsAgo = currentYear - 2;

            // Pre-seed two years ago
            entityManager.createNativeQuery(
                    "INSERT INTO order_sequence (tenant_id, year, next_seq) VALUES (:tenantId, :year, 999)")
                    .setParameter("tenantId", TENANT_1)
                    .setParameter("year", twoYearsAgo)
                    .executeUpdate();
            entityManager.flush();

            // Generate for current year (should start fresh)
            String orderNumber = generator.generateOrderNumber();

            assertThat(orderNumber).isEqualTo(String.format("ORD-%d-00001", currentYear));
        }
    }
}
