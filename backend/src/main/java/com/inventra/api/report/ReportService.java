package com.inventra.api.report;

import com.inventra.api.entity.MovementType;
import com.inventra.api.entity.OrderStatus;
import com.inventra.api.report.dto.*;
import com.inventra.api.exception.InvalidRequestException;
import com.inventra.api.tenant.TenantContext;
import com.inventra.api.util.LogSanitizer;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class ReportService {

    @PersistenceContext
    private EntityManager entityManager;

    public ReportService() {}

    @Transactional(readOnly = true)
    public InventorySummaryResponse getInventorySummary() {
        String tenantId = TenantContext.requireTenantId();
        log.info("Generating inventory summary for tenant: {}", LogSanitizer.sanitize(tenantId));

        String sql = """
            SELECT 
                COUNT(DISTINCT i.product_id) as total_skus,
                COALESCE(SUM(i.quantity_on_hand * p.unit_price), 0) as total_stock_value,
                -- "Low stock" is defined on AVAILABLE quantity (on hand minus reserved),
                -- matching InventoryItemRepository.findLowStock. This summary previously
                -- counted on-hand only, so the dashboard count and the low-stock list
                -- disagreed whenever any stock was reserved.
                COUNT(CASE WHEN (i.quantity_on_hand - i.quantity_reserved) <= i.reorder_point THEN 1 END) as low_stock_count,
                COALESCE(SUM(i.quantity_on_hand), 0) as total_quantity_on_hand,
                COALESCE(SUM(i.quantity_reserved), 0) as total_quantity_reserved
            FROM inventory_item i
            JOIN product p ON i.product_id = p.id
            WHERE i.tenant_id = :tenantId
            """;

        Query query = entityManager.createNativeQuery(sql);
        query.setParameter("tenantId", tenantId);

        Object[] result = (Object[]) query.getSingleResult();

        Long totalSkus = ((Number) result[0]).longValue();
        BigDecimal totalStockValue = new BigDecimal(result[1].toString());
        Long lowStockCount = ((Number) result[2]).longValue();
        Long totalQuantityOnHand = ((Number) result[3]).longValue();
        Long totalQuantityReserved = ((Number) result[4]).longValue();
        Long totalQuantityAvailable = totalQuantityOnHand - totalQuantityReserved;

        log.info("Inventory summary generated: {} SKUs, {} low stock items", totalSkus, lowStockCount);

        return new InventorySummaryResponse(
            totalSkus,
            totalStockValue,
            lowStockCount,
            totalQuantityOnHand,
            totalQuantityReserved,
            totalQuantityAvailable,
            Instant.now()
        );
    }

    @Transactional(readOnly = true)
    public StockMovementReportResponse getStockMovementReport(
            LocalDate startDate,
            LocalDate endDate,
            String groupBy) {
        
        String tenantId = TenantContext.requireTenantId();

        validateDateRange(startDate, endDate, "stock movement report");

        log.info("Generating stock movement report for tenant: {}, groupBy: {}, startDate: {}, endDate: {}",
                LogSanitizer.sanitize(tenantId), LogSanitizer.sanitize(groupBy), startDate, endDate);

        if (groupBy == null || (!groupBy.equals("type") && !groupBy.equals("date") && !groupBy.equals("date_type"))) {
            throw new InvalidRequestException("groupBy must be 'type', 'date', or 'date_type'");
        }

        List<MovementGroupResponse> movements = new ArrayList<>();

        if (groupBy.equals("type")) {
            movements = getMovementsByType(tenantId, startDate, endDate);
        } else if (groupBy.equals("date_type")) {
            movements = getMovementsByDateAndType(tenantId, startDate, endDate);
        } else {
            movements = getMovementsByDate(tenantId, startDate, endDate);
        }

        log.info("Stock movement report generated: {} groups", movements.size());

        return new StockMovementReportResponse(
            groupBy,
            startDate,
            endDate,
            movements,
            Instant.now()
        );
    }

    private List<MovementGroupResponse> getMovementsByType(String tenantId, LocalDate startDate, LocalDate endDate) {
        String sql = """
            SELECT 
                type,
                COUNT(*) as count,
                COALESCE(SUM(quantity), 0) as total_quantity
            FROM stock_movement
            WHERE tenant_id = :tenantId
                AND (:startDate IS NULL OR DATE(created_at) >= :startDate)
                AND (:endDate IS NULL OR DATE(created_at) <= :endDate)
            GROUP BY type
            ORDER BY type
            """;

        Query query = entityManager.createNativeQuery(sql);
        query.setParameter("tenantId", tenantId);
        query.setParameter("startDate", startDate);
        query.setParameter("endDate", endDate);

        @SuppressWarnings("unchecked")
        List<Object[]> results = query.getResultList();

        List<MovementGroupResponse> movements = new ArrayList<>();
        for (Object[] row : results) {
            MovementType type = MovementType.valueOf((String) row[0]);
            Long count = ((Number) row[1]).longValue();
            Long totalQuantity = ((Number) row[2]).longValue();

            movements.add(new MovementGroupResponse(type, null, count, totalQuantity));
        }

        return movements;
    }

    private List<MovementGroupResponse> getMovementsByDateAndType(String tenantId, LocalDate startDate, LocalDate endDate) {
        String sql = """
            SELECT
                type,
                DATE(created_at) as movement_date,
                COUNT(*) as count,
                COALESCE(SUM(quantity), 0) as total_quantity
            FROM stock_movement
            WHERE tenant_id = :tenantId
                AND (:startDate IS NULL OR DATE(created_at) >= :startDate)
                AND (:endDate IS NULL OR DATE(created_at) <= :endDate)
            GROUP BY type, DATE(created_at)
            ORDER BY movement_date, type
            """;

        Query query = entityManager.createNativeQuery(sql);
        query.setParameter("tenantId", tenantId);
        query.setParameter("startDate", startDate);
        query.setParameter("endDate", endDate);

        @SuppressWarnings("unchecked")
        List<Object[]> results = query.getResultList();

        List<MovementGroupResponse> movements = new ArrayList<>();
        for (Object[] row : results) {
            MovementType type = MovementType.valueOf((String) row[0]);
            LocalDate date = toLocalDate(row[1]);
            Long count = ((Number) row[2]).longValue();
            Long totalQuantity = ((Number) row[3]).longValue();
            movements.add(new MovementGroupResponse(type, date, count, totalQuantity));
        }

        return movements;
    }

    private List<MovementGroupResponse> getMovementsByDate(String tenantId, LocalDate startDate, LocalDate endDate) {
        String sql = """
            SELECT 
                DATE(created_at) as movement_date,
                COUNT(*) as count,
                COALESCE(SUM(quantity), 0) as total_quantity
            FROM stock_movement
            WHERE tenant_id = :tenantId
                AND (:startDate IS NULL OR DATE(created_at) >= :startDate)
                AND (:endDate IS NULL OR DATE(created_at) <= :endDate)
            GROUP BY DATE(created_at)
            ORDER BY movement_date
            """;

        Query query = entityManager.createNativeQuery(sql);
        query.setParameter("tenantId", tenantId);
        query.setParameter("startDate", startDate);
        query.setParameter("endDate", endDate);

        @SuppressWarnings("unchecked")
        List<Object[]> results = query.getResultList();

        List<MovementGroupResponse> movements = new ArrayList<>();
        for (Object[] row : results) {
            LocalDate date = toLocalDate(row[0]);
            Long count = ((Number) row[1]).longValue();
            Long totalQuantity = ((Number) row[2]).longValue();

            movements.add(new MovementGroupResponse(null, date, count, totalQuantity));
        }

        return movements;
    }

    /**
     * A SQL {@code DATE} arrives as either {@link java.sql.Date} or {@link LocalDate}
     * depending on the JDBC driver, so accept both.
     */
    private static LocalDate toLocalDate(Object value) {
        return switch (value) {
            case LocalDate localDate -> localDate;
            case java.sql.Date sqlDate -> sqlDate.toLocalDate();
            case java.time.temporal.TemporalAccessor temporal -> LocalDate.from(temporal);
            case null -> throw new IllegalStateException("Native query returned a null date");
            default -> throw new IllegalStateException(
                    "Unexpected date type from native query: " + value.getClass().getName());
        };
    }

    @Transactional(readOnly = true)
    public OrderSummaryResponse getOrderSummary(LocalDate startDate, LocalDate endDate) {
        String tenantId = TenantContext.requireTenantId();

        validateDateRange(startDate, endDate, "order summary report");

        log.info("Generating order summary for tenant: {}, startDate: {}, endDate: {}",
                LogSanitizer.sanitize(tenantId), startDate, endDate);

        List<OrderStatusGroupResponse> ordersByStatus = getOrdersByStatus(tenantId, startDate, endDate);

        Long totalOrders = ordersByStatus.stream()
                .mapToLong(OrderStatusGroupResponse::count)
                .sum();

        // GMV counts DELIVERED orders only
        BigDecimal totalGmv = getTotalGmv(tenantId, startDate, endDate);

        Double avgFulfillmentTime = getAverageFulfillmentTime(tenantId, startDate, endDate);

        log.info("Order summary generated: {} total orders, GMV: {}", totalOrders, totalGmv);

        return new OrderSummaryResponse(
            startDate,
            endDate,
            ordersByStatus,
            totalOrders,
            totalGmv,
            avgFulfillmentTime,
            Instant.now()
        );
    }

    private List<OrderStatusGroupResponse> getOrdersByStatus(String tenantId, LocalDate startDate, LocalDate endDate) {
        String sql = """
            SELECT 
                status,
                COUNT(*) as count,
                COALESCE(SUM(total_amount), 0) as total_amount
            FROM `order`
            WHERE tenant_id = :tenantId
                AND (:startDate IS NULL OR DATE(created_at) >= :startDate)
                AND (:endDate IS NULL OR DATE(created_at) <= :endDate)
            GROUP BY status
            ORDER BY status
            """;

        Query query = entityManager.createNativeQuery(sql);
        query.setParameter("tenantId", tenantId);
        query.setParameter("startDate", startDate);
        query.setParameter("endDate", endDate);

        @SuppressWarnings("unchecked")
        List<Object[]> results = query.getResultList();

        List<OrderStatusGroupResponse> groups = new ArrayList<>();
        for (Object[] row : results) {
            OrderStatus status = OrderStatus.valueOf((String) row[0]);
            Long count = ((Number) row[1]).longValue();
            BigDecimal totalAmount = new BigDecimal(row[2].toString());

            groups.add(new OrderStatusGroupResponse(status, count, totalAmount));
        }

        return groups;
    }

    private BigDecimal getTotalGmv(String tenantId, LocalDate startDate, LocalDate endDate) {
        String sql = """
            SELECT COALESCE(SUM(total_amount), 0)
            FROM `order`
            WHERE tenant_id = :tenantId
                AND status = 'DELIVERED'
                AND (:startDate IS NULL OR DATE(created_at) >= :startDate)
                AND (:endDate IS NULL OR DATE(created_at) <= :endDate)
            """;

        Query query = entityManager.createNativeQuery(sql);
        query.setParameter("tenantId", tenantId);
        query.setParameter("startDate", startDate);
        query.setParameter("endDate", endDate);

        Object result = query.getSingleResult();
        return new BigDecimal(result.toString());
    }

    private Double getAverageFulfillmentTime(String tenantId, LocalDate startDate, LocalDate endDate) {
        String sql = """
            SELECT AVG(TIMESTAMPDIFF(HOUR, submitted.changed_at, delivered.changed_at))
            FROM order_status_history submitted
            JOIN order_status_history delivered 
                ON submitted.order_id = delivered.order_id
            JOIN `order` o ON submitted.order_id = o.id
            WHERE o.tenant_id = :tenantId
                AND submitted.to_status = 'SUBMITTED'
                AND delivered.to_status = 'DELIVERED'
                AND (:startDate IS NULL OR DATE(o.created_at) >= :startDate)
                AND (:endDate IS NULL OR DATE(o.created_at) <= :endDate)
                AND submitted.changed_at < delivered.changed_at
            """;

        Query query = entityManager.createNativeQuery(sql);
        query.setParameter("tenantId", tenantId);
        query.setParameter("startDate", startDate);
        query.setParameter("endDate", endDate);

        Object result = query.getSingleResult();
        return result != null ? ((Number) result).doubleValue() : null;
    }

    @Transactional(readOnly = true)
    public TopProductsResponse getTopProducts(int days, int limit) {
        String tenantId = TenantContext.requireTenantId();
        log.info("Generating top products report for tenant: {}, days: {}, limit: {}",
                LogSanitizer.sanitize(tenantId), days, limit);

        if (days != ReportConstants.MIN_DAYS_THRESHOLD
                && days != ReportConstants.MID_DAYS_THRESHOLD
                && days != ReportConstants.MAX_DAYS_THRESHOLD) {
            throw new InvalidRequestException("days must be 30, 60, or 90");
        }
        if (limit < ReportConstants.MIN_PAGE_SIZE || limit > ReportConstants.MAX_PAGE_SIZE) {
            throw new InvalidRequestException("limit must be between 1 and 100");
        }

        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(days);

        String sql = """
            SELECT 
                p.id as product_id,
                p.sku,
                p.name,
                COUNT(DISTINCT o.id) as order_count,
                COALESCE(SUM(oi.quantity), 0) as total_quantity_sold,
                COALESCE(SUM(oi.total_price), 0) as total_revenue
            FROM order_item oi
            JOIN `order` o ON oi.order_id = o.id
            JOIN product p ON oi.product_id = p.id
            WHERE o.tenant_id = :tenantId
                AND o.status = 'DELIVERED'
                AND o.created_at >= :startDate
            GROUP BY p.id, p.sku, p.name
            ORDER BY total_quantity_sold DESC
            LIMIT :limit
            """;

        Query query = entityManager.createNativeQuery(sql);
        query.setParameter("tenantId", tenantId);
        query.setParameter("startDate", startDate);
        query.setParameter("limit", limit);

        @SuppressWarnings("unchecked")
        List<Object[]> results = query.getResultList();

        List<TopProductResponse> products = new ArrayList<>();
        for (Object[] row : results) {
            String productId = (String) row[0];
            String sku = (String) row[1];
            String name = (String) row[2];
            Long orderCount = ((Number) row[3]).longValue();
            Long totalQuantitySold = ((Number) row[4]).longValue();
            BigDecimal totalRevenue = new BigDecimal(row[5].toString());

            products.add(new TopProductResponse(
                productId, sku, name, orderCount, totalQuantitySold, totalRevenue
            ));
        }

        log.info("Top products report generated: {} products", products.size());

        return new TopProductsResponse(
            days,
            limit,
            startDate,
            endDate,
            products,
            Instant.now()
        );
    }

    // Package-private for direct unit testing of the boundary/overflow logic.
    void validateDateRange(LocalDate startDate, LocalDate endDate, String reportName) {
        if (startDate == null || endDate == null) {
            throw new InvalidRequestException("startDate and endDate are required for the " + reportName);
        }
        if (endDate.isBefore(startDate)) {
            throw new InvalidRequestException("endDate must not be before startDate");
        }
        // Compare via epoch days so dates near LocalDate.MAX don't overflow
        // LocalDate.plusDays. Inclusive boundary is preserved: a span of exactly
        // MAX_DATE_RANGE_DAYS is still allowed.
        long rangeDays = endDate.toEpochDay() - startDate.toEpochDay();
        if (rangeDays > ReportConstants.MAX_DATE_RANGE_DAYS) {
            throw new InvalidRequestException("Date range must not exceed " + ReportConstants.MAX_DATE_RANGE_DAYS + " days");
        }
    }
}
