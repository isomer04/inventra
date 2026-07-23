package com.inventra.api.report;

import com.inventra.api.report.dto.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.inventra.api.util.LogSanitizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/reports")
@Tag(name = "Reports", description = "Aggregate reporting endpoints for operational visibility")
@RequiredArgsConstructor
@Slf4j
public class ReportController {

    private final ReportService reportService;

    @GetMapping("/inventory-summary")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'WAREHOUSE_STAFF', 'VIEWER')")
    @Operation(
        summary = "Get inventory summary",
        description = "Returns aggregate metrics including total SKUs, stock value, low-stock count, and quantity totals",
        responses = {
            @ApiResponse(
                responseCode = "200",
                description = "Inventory summary retrieved successfully",
                content = @Content(schema = @Schema(implementation = InventorySummaryResponse.class))
            ),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Forbidden")
        }
    )
    public ResponseEntity<InventorySummaryResponse> getInventorySummary() {
        log.info("GET /api/v1/reports/inventory-summary");
        InventorySummaryResponse response = reportService.getInventorySummary();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/stock-movements")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Operation(
        summary = "Get stock movement report",
        description = "Returns stock movements grouped by type or date. startDate and endDate are required; max range 366 days.",
        responses = {
            @ApiResponse(
                responseCode = "200",
                description = "Stock movement report retrieved successfully",
                content = @Content(schema = @Schema(implementation = StockMovementReportResponse.class))
            ),
            @ApiResponse(responseCode = "400", description = "Invalid parameters or missing date range"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Forbidden - ADMIN or MANAGER role required")
        }
    )
    public ResponseEntity<StockMovementReportResponse> getStockMovementReport(
            @Parameter(description = "Start date — required (ISO 8601: YYYY-MM-DD)", example = "2026-01-01", required = true)
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            
            @Parameter(description = "End date — required (ISO 8601: YYYY-MM-DD)", example = "2026-03-31", required = true)
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            
            @Parameter(description = "Group by 'type', 'date', or 'date_type' (date × type for stacked charts)", example = "type")
            @RequestParam(required = false, defaultValue = "type") String groupBy) {
        
        log.info("GET /api/v1/reports/stock-movements - startDate: {}, endDate: {}, groupBy: {}",
                startDate, endDate, LogSanitizer.sanitize(groupBy));
        
        StockMovementReportResponse response = reportService.getStockMovementReport(startDate, endDate, groupBy);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/order-summary")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'WAREHOUSE_STAFF', 'VIEWER')")
    @Operation(
        summary = "Get order summary",
        description = "Returns order metrics including status breakdown, total GMV, and average fulfillment time. startDate and endDate are required; max range 366 days.",
        responses = {
            @ApiResponse(
                responseCode = "200",
                description = "Order summary retrieved successfully",
                content = @Content(schema = @Schema(implementation = OrderSummaryResponse.class))
            ),
            @ApiResponse(responseCode = "400", description = "Invalid date format or missing date range"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Forbidden")
        }
    )
    public ResponseEntity<OrderSummaryResponse> getOrderSummary(
            @Parameter(description = "Start date — required (ISO 8601: YYYY-MM-DD)", example = "2026-01-01", required = true)
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            
            @Parameter(description = "End date — required (ISO 8601: YYYY-MM-DD)", example = "2026-03-31", required = true)
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        
        log.info("GET /api/v1/reports/order-summary - startDate: {}, endDate: {}", startDate, endDate);
        
        OrderSummaryResponse response = reportService.getOrderSummary(startDate, endDate);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/top-products")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'WAREHOUSE_STAFF', 'VIEWER')")
    @Operation(
        summary = "Get top products by sales volume",
        description = "Returns top N products by quantity sold in the last X days (only DELIVERED orders)",
        responses = {
            @ApiResponse(
                responseCode = "200",
                description = "Top products report retrieved successfully",
                content = @Content(schema = @Schema(implementation = TopProductsResponse.class))
            ),
            @ApiResponse(responseCode = "400", description = "Invalid parameters (days must be 30/60/90, limit must be 1-100)"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Forbidden")
        }
    )
    public ResponseEntity<TopProductsResponse> getTopProducts(
            @Parameter(description = "Number of days to look back (30, 60, or 90)", example = "30")
            @RequestParam(required = false, defaultValue = "30") int days,
            
            @Parameter(description = "Maximum number of products to return (1-100)", example = "10")
            @RequestParam(required = false, defaultValue = "10") int limit) {
        
        log.info("GET /api/v1/reports/top-products - days: {}, limit: {}", days, limit);
        
        TopProductsResponse response = reportService.getTopProducts(days, limit);
        return ResponseEntity.ok(response);
    }
}
