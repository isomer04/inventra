package com.inventra.api.report;

import com.inventra.api.BaseIntegrationTest;
import com.inventra.api.entity.*;
import com.inventra.api.exception.InvalidRequestException;
import com.inventra.api.order.OrderItemRepository;
import com.inventra.api.order.OrderRepository;
import com.inventra.api.report.dto.*;
import com.inventra.api.repository.*;
import com.inventra.api.tenant.TenantContext;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for {@link ReportService}.
 *
 * <p>Tests comprehensive reporting functionality including:
 * <ul>
 *   <li>Inventory summary reports</li>
 *   <li>Stock movement reports with grouping (type, date, date_type)</li>
 *   <li>Order summary reports with date ranges</li>
 *   <li>Top products ranking with revenue calculations</li>
 *   <li>Date range validation and boundary conditions</li>
 *   <li>Tenant isolation in reports</li>
 *   <li>Empty state handling</li>
 * </ul>
 */
@SpringBootTest
@Sql(scripts = "/test-data/cleanup.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@DisplayName("ReportService")
class ReportServiceTest extends BaseIntegrationTest {

    @Autowired
    private ReportService reportService;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private InventoryItemRepository inventoryItemRepository;

    @Autowired
    private StockMovementRepository stockMovementRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @PersistenceContext
    private EntityManager entityManager;

    private Tenant testTenant;
    private Product product1;
    private Product product2;
    private Product product3;
    private Customer customer;
    private User testUser;

    @BeforeEach
    void setUp() {
        // Clear the persistence context from any previous test method so that
        // entities Hibernate thinks are managed aren't merged onto rows that
        // @Sql(BEFORE_TEST_METHOD) just truncated.
        entityManager.clear();

        testTenant = Tenant.builder()
                .id("tenant-report-test-001")
                .name("Report Test Tenant")
                .slug("report-test")
                .build();
        testTenant = tenantRepository.save(testTenant);

        TenantContext.setTenantId(testTenant.getId());

        testUser = User.builder()
                .id("user-report-test-001")
                .tenant(testTenant)
                .email("report-test@example.com")
                .passwordHash(passwordEncoder.encode("password123"))
                .firstName("Report")
                .lastName("Tester")
                .role(UserRole.ADMIN)
                .status(UserStatus.ACTIVE)
                .build();
        testUser = userRepository.save(testUser);

        product1 = createProduct("PROD-001", "Product One", "10.00");
        product2 = createProduct("PROD-002", "Product Two", "20.00");
        product3 = createProduct("PROD-003", "Product Three", "30.00");

        customer = Customer.builder()
                .id("customer-001")
                .tenantId(testTenant.getId())
                .name("Test Customer")
                .email("customer@example.com")
                .phone("555-0100")
                .build();
        customer = customerRepository.save(customer);
    }

    // Leave @UuidGenerator ids unset: an assigned id makes save() merge onto a row
    // @Sql just truncated, which fails instead of inserting.
    private Product createProduct(String sku, String name, String unitPrice) {
        Product product = Product.builder()
                .sku(sku)
                .name(name)
                .unitPrice(new BigDecimal(unitPrice))
                .status(ProductStatus.ACTIVE)
                .tenant(testTenant)
                .build();
        return productRepository.save(product);
    }

    private InventoryItem createInventoryItem(Product product, int quantityOnHand, int quantityReserved, int reorderPoint) {
        InventoryItem item = InventoryItem.builder()
                .tenantId(testTenant.getId())
                .productId(product.getId())
                .quantityOnHand(quantityOnHand)
                .quantityReserved(quantityReserved)
                .reorderPoint(reorderPoint)
                .build();
        return inventoryItemRepository.save(item);
    }

    private StockMovement createStockMovement(Product product, MovementType type, int quantity, LocalDate date) {
        StockMovement movement = StockMovement.builder()
                .tenantId(testTenant.getId())
                .productId(product.getId())
                .type(type)
                .quantity(quantity)
                .notes("Test movement")
                .createdBy(testUser.getId())
                .build();
        return stockMovementRepository.save(movement);
    }

    @Nested
    @DisplayName("getInventorySummary()")
    class GetInventorySummary {

        @Test
        @Transactional
        @DisplayName("returns summary with total SKUs and stock value")
        void returnsSummaryWithTotals() {
            createInventoryItem(product1, 100, 10, 20);
            createInventoryItem(product2, 50, 5, 15);
            createInventoryItem(product3, 25, 0, 10);

            InventorySummaryResponse response = reportService.getInventorySummary();

            assertThat(response.totalSkus()).isEqualTo(3);
            // Stock value = (100 * 10) + (50 * 20) + (25 * 30) = 1000 + 1000 + 750 = 2750
            assertThat(response.totalStockValue()).isEqualByComparingTo("2750.00");
            assertThat(response.totalQuantityOnHand()).isEqualTo(175);
            assertThat(response.totalQuantityReserved()).isEqualTo(15);
            assertThat(response.totalQuantityAvailable()).isEqualTo(160);
        }

        @Test
        @Transactional
        @DisplayName("counts low stock items correctly")
        void countsLowStockItems() {
            createInventoryItem(product1, 100, 10, 20); // Available: 90, above reorder point
            createInventoryItem(product2, 10, 5, 15);   // Available: 5, below reorder point
            createInventoryItem(product3, 5, 0, 10);    // Available: 5, below reorder point

            InventorySummaryResponse response = reportService.getInventorySummary();

            assertThat(response.lowStockCount()).isEqualTo(2);
        }

        @Test
        @Transactional
        @DisplayName("returns zero values for tenant with no inventory")
        void returnsZeroValuesForEmptyInventory() {
            InventorySummaryResponse response = reportService.getInventorySummary();

            assertThat(response.totalSkus()).isZero();
            assertThat(response.totalStockValue()).isEqualByComparingTo("0.00");
            assertThat(response.lowStockCount()).isZero();
            assertThat(response.totalQuantityOnHand()).isZero();
            assertThat(response.totalQuantityReserved()).isZero();
            assertThat(response.totalQuantityAvailable()).isZero();
        }

        @Test
        @Transactional
        @DisplayName("includes timestamp in response")
        void includesTimestamp() {
            createInventoryItem(product1, 50, 0, 10);

            InventorySummaryResponse response = reportService.getInventorySummary();

            assertThat(response.generatedAt()).isNotNull();
            assertThat(response.generatedAt()).isBeforeOrEqualTo(Instant.now());
        }

        @Test
        @Transactional
        @DisplayName("isolates by tenant")
        void isolatesByTenant() {
            createInventoryItem(product1, 100, 0, 10);

            // Create another tenant with inventory
            Tenant otherTenant = Tenant.builder()
                    .id("tenant-other-001")
                    .name("Other Tenant")
                    .slug("other")
                    .build();
            otherTenant = tenantRepository.save(otherTenant);

            Product otherProduct = Product.builder()
                    .sku("OTHER-001")
                    .name("Other Product")
                    .unitPrice(new BigDecimal("50.00"))
                    .status(ProductStatus.ACTIVE)
                    .tenant(otherTenant)
                    .build();
            otherProduct = productRepository.save(otherProduct);

            InventoryItem otherItem = InventoryItem.builder()
                    .tenantId(otherTenant.getId())
                    .productId(otherProduct.getId())
                    .quantityOnHand(500)
                    .quantityReserved(0)
                    .reorderPoint(50)
                    .build();
            inventoryItemRepository.save(otherItem);

            // Query for testTenant only
            InventorySummaryResponse response = reportService.getInventorySummary();

            assertThat(response.totalSkus()).isEqualTo(1);
            assertThat(response.totalQuantityOnHand()).isEqualTo(100);
        }
    }

    @Nested
    @DisplayName("getStockMovementReport()")
    class GetStockMovementReport {

        @Test
        @Transactional
        @DisplayName("requires both startDate and endDate")
        void requiresBothDates() {
            assertThatThrownBy(() -> reportService.getStockMovementReport(null, LocalDate.now(), "type"))
                    .isInstanceOf(InvalidRequestException.class)
                    .hasMessageContaining("startDate and endDate are required");

            assertThatThrownBy(() -> reportService.getStockMovementReport(LocalDate.now(), null, "type"))
                    .isInstanceOf(InvalidRequestException.class)
                    .hasMessageContaining("startDate and endDate are required");
        }

        @Test
        @Transactional
        @DisplayName("validates endDate is not before startDate")
        void validatesDateOrder() {
            LocalDate start = LocalDate.of(2026, 6, 1);
            LocalDate end = LocalDate.of(2026, 5, 1);

            assertThatThrownBy(() -> reportService.getStockMovementReport(start, end, "type"))
                    .isInstanceOf(InvalidRequestException.class)
                    .hasMessageContaining("endDate must not be before startDate");
        }

        @Test
        @Transactional
        @DisplayName("validates date range does not exceed 366 days")
        void validatesDateRangeLimit() {
            LocalDate start = LocalDate.of(2025, 1, 1);
            LocalDate end = LocalDate.of(2026, 2, 1); // More than 366 days

            assertThatThrownBy(() -> reportService.getStockMovementReport(start, end, "type"))
                    .isInstanceOf(InvalidRequestException.class)
                    .hasMessageContaining("Date range must not exceed 366 days");
        }

        @Test
        @Transactional
        @DisplayName("validates groupBy parameter")
        void validatesGroupByParameter() {
            LocalDate start = LocalDate.now().minusDays(7);
            LocalDate end = LocalDate.now();

            assertThatThrownBy(() -> reportService.getStockMovementReport(start, end, "invalid"))
                    .isInstanceOf(InvalidRequestException.class)
                    .hasMessageContaining("groupBy must be 'type', 'date', or 'date_type'");

            assertThatThrownBy(() -> reportService.getStockMovementReport(start, end, null))
                    .isInstanceOf(InvalidRequestException.class)
                    .hasMessageContaining("groupBy must be 'type', 'date', or 'date_type'");
        }

        @Test
        @Transactional
        @DisplayName("groups movements by type")
        void groupsMovementsByType() {
            LocalDate start = LocalDate.now().minusDays(30);
            LocalDate end = LocalDate.now();

            createStockMovement(product1, MovementType.RECEIPT, 100, LocalDate.now());
            createStockMovement(product1, MovementType.RECEIPT, 50, LocalDate.now());
            createStockMovement(product2, MovementType.ADJUSTMENT, 10, LocalDate.now());
            createStockMovement(product3, MovementType.DEDUCTION, -5, LocalDate.now());

            StockMovementReportResponse response = reportService.getStockMovementReport(start, end, "type");

            assertThat(response.groupBy()).isEqualTo("type");
            assertThat(response.movements()).hasSizeGreaterThanOrEqualTo(2);

            // Find RECEIPT group
            MovementGroupResponse receiptGroup = response.movements().stream()
                    .filter(g -> g.type() == MovementType.RECEIPT)
                    .findFirst()
                    .orElseThrow();

            assertThat(receiptGroup.count()).isEqualTo(2);
            assertThat(receiptGroup.totalQuantity()).isEqualTo(150);
        }

        @Test
        @Transactional
        @DisplayName("groups movements by date")
        void groupsMovementsByDate() {
            LocalDate start = LocalDate.now().minusDays(30);
            LocalDate end = LocalDate.now();
            LocalDate today = LocalDate.now();

            createStockMovement(product1, MovementType.RECEIPT, 100, today);
            createStockMovement(product2, MovementType.RECEIPT, 50, today);

            StockMovementReportResponse response = reportService.getStockMovementReport(start, end, "date");

            assertThat(response.groupBy()).isEqualTo("date");
            assertThat(response.movements()).isNotEmpty();
        }

        @Test
        @Transactional
        @DisplayName("groups movements by date and type")
        void groupsMovementsByDateAndType() {
            LocalDate start = LocalDate.now().minusDays(30);
            LocalDate end = LocalDate.now();

            createStockMovement(product1, MovementType.RECEIPT, 100, LocalDate.now());
            createStockMovement(product2, MovementType.ADJUSTMENT, 10, LocalDate.now());

            StockMovementReportResponse response = reportService.getStockMovementReport(start, end, "date_type");

            assertThat(response.groupBy()).isEqualTo("date_type");
            assertThat(response.movements()).isNotEmpty();
        }

        @Test
        @Transactional
        @DisplayName("returns empty list when no movements in date range")
        void returnsEmptyListForNoMovements() {
            LocalDate start = LocalDate.of(2020, 1, 1);
            LocalDate end = LocalDate.of(2020, 1, 31);

            StockMovementReportResponse response = reportService.getStockMovementReport(start, end, "type");

            assertThat(response.movements()).isEmpty();
        }
    }

    @Nested
    @DisplayName("getOrderSummary()")
    class GetOrderSummary {

        @Test
        @Transactional
        @DisplayName("requires both startDate and endDate")
        void requiresBothDates() {
            assertThatThrownBy(() -> reportService.getOrderSummary(null, LocalDate.now()))
                    .isInstanceOf(InvalidRequestException.class)
                    .hasMessageContaining("startDate and endDate are required");

            assertThatThrownBy(() -> reportService.getOrderSummary(LocalDate.now(), null))
                    .isInstanceOf(InvalidRequestException.class)
                    .hasMessageContaining("startDate and endDate are required");
        }

        @Test
        @Transactional
        @DisplayName("validates date order")
        void validatesDateOrder() {
            LocalDate start = LocalDate.of(2026, 6, 1);
            LocalDate end = LocalDate.of(2026, 5, 1);

            assertThatThrownBy(() -> reportService.getOrderSummary(start, end))
                    .isInstanceOf(InvalidRequestException.class)
                    .hasMessageContaining("endDate must not be before startDate");
        }

        @Test
        @Transactional
        @DisplayName("validates date range limit")
        void validatesDateRangeLimit() {
            LocalDate start = LocalDate.of(2025, 1, 1);
            LocalDate end = LocalDate.of(2026, 2, 1);

            assertThatThrownBy(() -> reportService.getOrderSummary(start, end))
                    .isInstanceOf(InvalidRequestException.class)
                    .hasMessageContaining("Date range must not exceed 366 days");
        }

        @Test
        @Transactional
        @DisplayName("groups orders by status")
        void groupsOrdersByStatus() {
            LocalDate start = LocalDate.now().minusDays(30);
            LocalDate end = LocalDate.now();

            // Create orders with different statuses
            createOrder("ORD-001", OrderStatus.SUBMITTED, "100.00");
            createOrder("ORD-002", OrderStatus.SUBMITTED, "200.00");
            createOrder("ORD-003", OrderStatus.DELIVERED, "300.00");
            createOrder("ORD-004", OrderStatus.CANCELLED, "150.00");

            OrderSummaryResponse response = reportService.getOrderSummary(start, end);

            assertThat(response.totalOrders()).isEqualTo(4);
            assertThat(response.ordersByStatus()).hasSizeGreaterThanOrEqualTo(3);

            // Find SUBMITTED group
            OrderStatusGroupResponse submittedGroup = response.ordersByStatus().stream()
                    .filter(g -> g.status() == OrderStatus.SUBMITTED)
                    .findFirst()
                    .orElseThrow();

            assertThat(submittedGroup.count()).isEqualTo(2);
            assertThat(submittedGroup.totalAmount()).isEqualByComparingTo("300.00");
        }

        @Test
        @Transactional
        @DisplayName("calculates GMV from DELIVERED orders only")
        void calculatesGmvFromDeliveredOrders() {
            LocalDate start = LocalDate.now().minusDays(30);
            LocalDate end = LocalDate.now();

            createOrder("ORD-001", OrderStatus.SUBMITTED, "100.00");
            createOrder("ORD-002", OrderStatus.DELIVERED, "200.00");
            createOrder("ORD-003", OrderStatus.DELIVERED, "300.00");
            createOrder("ORD-004", OrderStatus.CANCELLED, "150.00");

            OrderSummaryResponse response = reportService.getOrderSummary(start, end);

            // GMV = sum of DELIVERED orders only = 200 + 300 = 500
            assertThat(response.totalGmv()).isEqualByComparingTo("500.00");
        }

        @Test
        @Transactional
        @DisplayName("returns null average fulfillment time when no delivered orders")
        void returnsNullFulfillmentTimeWhenNoDeliveredOrders() {
            LocalDate start = LocalDate.now().minusDays(30);
            LocalDate end = LocalDate.now();

            createOrder("ORD-001", OrderStatus.SUBMITTED, "100.00");

            OrderSummaryResponse response = reportService.getOrderSummary(start, end);

            assertThat(response.averageFulfillmentTimeHours()).isNull();
        }

        @Test
        @Transactional
        @DisplayName("returns zero GMV when no orders")
        void returnsZeroGmvForNoOrders() {
            LocalDate start = LocalDate.now().minusDays(30);
            LocalDate end = LocalDate.now();

            OrderSummaryResponse response = reportService.getOrderSummary(start, end);

            assertThat(response.totalOrders()).isZero();
            assertThat(response.totalGmv()).isEqualByComparingTo("0.00");
            assertThat(response.ordersByStatus()).isEmpty();
        }
    }

    @Nested
    @DisplayName("getTopProducts()")
    class GetTopProducts {

        @Test
        @Transactional
        @DisplayName("validates days parameter (30, 60, or 90)")
        void validatesDaysParameter() {
            assertThatThrownBy(() -> reportService.getTopProducts(15, 10))
                    .isInstanceOf(InvalidRequestException.class)
                    .hasMessageContaining("days must be 30, 60, or 90");

            assertThatThrownBy(() -> reportService.getTopProducts(100, 10))
                    .isInstanceOf(InvalidRequestException.class)
                    .hasMessageContaining("days must be 30, 60, or 90");
        }

        @Test
        @Transactional
        @DisplayName("validates limit parameter (1-100)")
        void validatesLimitParameter() {
            assertThatThrownBy(() -> reportService.getTopProducts(30, 0))
                    .isInstanceOf(InvalidRequestException.class)
                    .hasMessageContaining("limit must be between 1 and 100");

            assertThatThrownBy(() -> reportService.getTopProducts(30, 101))
                    .isInstanceOf(InvalidRequestException.class)
                    .hasMessageContaining("limit must be between 1 and 100");
        }

        @Test
        @Transactional
        @DisplayName("ranks products by total quantity sold")
        void ranksProductsByQuantitySold() {
            // Create delivered orders
            Order order1 = createOrder("ORD-001", OrderStatus.DELIVERED, "0");
            createOrderItem(order1, product1, 100, "10.00"); // Product1: 100 units
            createOrderItem(order1, product2, 50, "20.00");  // Product2: 50 units

            Order order2 = createOrder("ORD-002", OrderStatus.DELIVERED, "0");
            createOrderItem(order2, product2, 75, "20.00");  // Product2: total 125 units
            createOrderItem(order2, product3, 25, "30.00");  // Product3: 25 units

            TopProductsResponse response = reportService.getTopProducts(30, 10);

            assertThat(response.days()).isEqualTo(30);
            assertThat(response.limit()).isEqualTo(10);
            assertThat(response.products()).hasSizeGreaterThanOrEqualTo(3);

            // Product2 should be first (125 units), then Product1 (100 units), then Product3 (25 units)
            assertThat(response.products().get(0).sku()).isEqualTo("PROD-002");
            assertThat(response.products().get(0).totalQuantitySold()).isEqualTo(125);

            assertThat(response.products().get(1).sku()).isEqualTo("PROD-001");
            assertThat(response.products().get(1).totalQuantitySold()).isEqualTo(100);
        }

        @Test
        @Transactional
        @DisplayName("calculates total revenue per product")
        void calculatesTotalRevenuePerProduct() {
            Order order = createOrder("ORD-001", OrderStatus.DELIVERED, "0");
            createOrderItem(order, product1, 10, "10.00"); // Revenue: 100.00

            TopProductsResponse response = reportService.getTopProducts(30, 10);

            TopProductResponse topProduct = response.products().stream()
                    .filter(p -> p.sku().equals("PROD-001"))
                    .findFirst()
                    .orElseThrow();

            assertThat(topProduct.totalRevenue()).isEqualByComparingTo("100.00");
        }

        @Test
        @Transactional
        @DisplayName("respects limit parameter")
        void respectsLimitParameter() {
            // Create multiple products and orders
            for (int i = 0; i < 15; i++) {
                Product p = createProduct("PROD-" + String.format("%03d", i + 100), "Product " + i, "10.00");
                Order order = createOrder("ORD-" + String.format("%03d", i + 100), OrderStatus.DELIVERED, "0");
                createOrderItem(order, p, 10, "10.00");
            }

            TopProductsResponse response = reportService.getTopProducts(30, 5);

            assertThat(response.products()).hasSizeLessThanOrEqualTo(5);
        }

        @Test
        @Transactional
        @DisplayName("only includes DELIVERED orders")
        void onlyIncludesDeliveredOrders() {
            Order deliveredOrder = createOrder("ORD-001", OrderStatus.DELIVERED, "0");
            createOrderItem(deliveredOrder, product1, 100, "10.00");

            Order submittedOrder = createOrder("ORD-002", OrderStatus.SUBMITTED, "0");
            createOrderItem(submittedOrder, product2, 500, "20.00"); // Should not count

            TopProductsResponse response = reportService.getTopProducts(30, 10);

            // Only product1 should appear
            assertThat(response.products()).hasSize(1);
            assertThat(response.products().get(0).sku()).isEqualTo("PROD-001");
        }

        @Test
        @Transactional
        @DisplayName("returns empty list when no products sold")
        void returnsEmptyListWhenNoProductsSold() {
            TopProductsResponse response = reportService.getTopProducts(30, 10);

            assertThat(response.products()).isEmpty();
        }
    }

    private Order createOrder(String orderNumber, OrderStatus status, String totalAmount) {
        Order order = Order.builder()
                .id("order-" + orderNumber)
                .tenantId(testTenant.getId())
                .orderNumber(orderNumber)
                .customer(customer)
                .status(status)
                .totalAmount(new BigDecimal(totalAmount))
                .createdBy(testUser)
                .build();
        return orderRepository.save(order);
    }

    private OrderItem createOrderItem(Order order, Product product, int quantity, String unitPrice) {
        OrderItem item = OrderItem.builder()
                .id("item-" + order.getOrderNumber() + "-" + product.getSku())
                .order(order)
                .product(product)
                .quantity(quantity)
                .unitPrice(new BigDecimal(unitPrice))
                .totalPrice(new BigDecimal(unitPrice).multiply(new BigDecimal(quantity)))
                .build();
        return orderItemRepository.save(item);
    }
}
