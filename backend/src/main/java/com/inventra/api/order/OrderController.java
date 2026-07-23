package com.inventra.api.order;

import com.inventra.api.entity.OrderStatus;
import com.inventra.api.entity.User;
import com.inventra.api.order.dto.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
@Tag(name = "Orders", description = "Order lifecycle management")
@SecurityRequirement(name = "bearerAuth")
public class OrderController {
    
    private final OrderService orderService;
    private final OrderStatusHistoryService historyService;
    
    @GetMapping
    // Explicit annotation — all authenticated roles may list orders.
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'WAREHOUSE_STAFF', 'VIEWER')")
    @Operation(summary = "List orders", description = "Get paginated list of orders with optional filters")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Orders retrieved successfully"),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<Page<OrderSummaryResponse>> listOrders(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) String customerId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime endDate) {

        // OffsetDateTime is used at the HTTP boundary so @DateTimeFormat actually
        // applies (Spring's DateTimeFormatAnnotationFormatterFactory does not
        // support Instant). The service layer takes Instant for UTC consistency.
        Instant start = startDate != null ? startDate.toInstant() : null;
        Instant end   = endDate   != null ? endDate.toInstant()   : null;

        return ResponseEntity.ok(orderService.listOrders(pageable, status, customerId, start, end));
    }
    
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'WAREHOUSE_STAFF')")
    @Operation(summary = "Create order", description = "Create a new order in DRAFT status")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Order created successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request"),
        @ApiResponse(responseCode = "403", description = "Insufficient permissions"),
        @ApiResponse(responseCode = "404", description = "Customer or product not found")
    })
    public ResponseEntity<OrderResponse> createOrder(
            @Valid @RequestBody CreateOrderRequest request,
            @AuthenticationPrincipal User currentUser) {

        OrderResponse response = orderService.createOrder(request, currentUser.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
    
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'WAREHOUSE_STAFF', 'VIEWER')")
    @Operation(summary = "Get order", description = "Get order details by ID")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Order retrieved successfully"),
        @ApiResponse(responseCode = "404", description = "Order not found")
    })
    public ResponseEntity<OrderResponse> getOrder(@PathVariable String id) {
        return ResponseEntity.ok(orderService.getOrder(id));
    }
    
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Operation(summary = "Update order", description = "Update a DRAFT order's items or notes")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Order updated successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request or order not in DRAFT status"),
        @ApiResponse(responseCode = "403", description = "Insufficient permissions"),
        @ApiResponse(responseCode = "404", description = "Order not found")
    })
    public ResponseEntity<OrderResponse> updateOrder(
            @PathVariable String id,
            @Valid @RequestBody UpdateOrderRequest request,
            @AuthenticationPrincipal User currentUser) {

        return ResponseEntity.ok(orderService.updateOrder(id, request, currentUser.getId()));
    }
    
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Operation(summary = "Delete order", description = "Delete a DRAFT order")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Order deleted successfully"),
        @ApiResponse(responseCode = "400", description = "Order not in DRAFT status"),
        @ApiResponse(responseCode = "403", description = "Insufficient permissions"),
        @ApiResponse(responseCode = "404", description = "Order not found")
    })
    public ResponseEntity<Void> deleteOrder(@PathVariable String id) {
        orderService.deleteOrder(id);
        return ResponseEntity.noContent().build();
    }
    
    @PostMapping("/{id}/submit")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Operation(summary = "Submit order", description = "Transition order from DRAFT to SUBMITTED, reserving stock")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Order submitted successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid transition or insufficient stock"),
        @ApiResponse(responseCode = "403", description = "Insufficient permissions"),
        @ApiResponse(responseCode = "404", description = "Order not found")
    })
    public ResponseEntity<OrderResponse> submitOrder(
            @PathVariable String id,
            @Valid @RequestBody(required = false) TransitionRequest request,
            @AuthenticationPrincipal User currentUser) {

        return ResponseEntity.ok(orderService.submitOrder(id, request != null ? request : new TransitionRequest(), currentUser.getId()));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Operation(summary = "Approve order", description = "Transition order from SUBMITTED to APPROVED")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Order approved successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid transition"),
        @ApiResponse(responseCode = "403", description = "Insufficient permissions"),
        @ApiResponse(responseCode = "404", description = "Order not found")
    })
    public ResponseEntity<OrderResponse> approveOrder(
            @PathVariable String id,
            @Valid @RequestBody(required = false) TransitionRequest request,
            @AuthenticationPrincipal User currentUser) {

        return ResponseEntity.ok(orderService.approveOrder(id, request != null ? request : new TransitionRequest(), currentUser.getId()));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Operation(summary = "Reject order", description = "Transition order from SUBMITTED to REJECTED, releasing stock")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Order rejected successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid transition"),
        @ApiResponse(responseCode = "403", description = "Insufficient permissions"),
        @ApiResponse(responseCode = "404", description = "Order not found")
    })
    public ResponseEntity<OrderResponse> rejectOrder(
            @PathVariable String id,
            @Valid @RequestBody(required = false) TransitionRequest request,
            @AuthenticationPrincipal User currentUser) {

        return ResponseEntity.ok(orderService.rejectOrder(id, request != null ? request : new TransitionRequest(), currentUser.getId()));
    }

    @PostMapping("/{id}/start-picking")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'WAREHOUSE_STAFF')")
    @Operation(summary = "Start picking", description = "Transition order from APPROVED to PICKING")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Order picking started successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid transition"),
        @ApiResponse(responseCode = "403", description = "Insufficient permissions"),
        @ApiResponse(responseCode = "404", description = "Order not found")
    })
    public ResponseEntity<OrderResponse> startPicking(
            @PathVariable String id,
            @Valid @RequestBody(required = false) TransitionRequest request,
            @AuthenticationPrincipal User currentUser) {

        return ResponseEntity.ok(orderService.startPicking(id, request != null ? request : new TransitionRequest(), currentUser.getId()));
    }

    @PostMapping("/{id}/ship")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'WAREHOUSE_STAFF')")
    @Operation(summary = "Ship order", description = "Transition order from PICKING to SHIPPED")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Order shipped successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid transition"),
        @ApiResponse(responseCode = "403", description = "Insufficient permissions"),
        @ApiResponse(responseCode = "404", description = "Order not found")
    })
    public ResponseEntity<OrderResponse> shipOrder(
            @PathVariable String id,
            @Valid @RequestBody(required = false) TransitionRequest request,
            @AuthenticationPrincipal User currentUser) {

        return ResponseEntity.ok(orderService.shipOrder(id, request != null ? request : new TransitionRequest(), currentUser.getId()));
    }

    @PostMapping("/{id}/deliver")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'WAREHOUSE_STAFF')")
    @Operation(summary = "Deliver order", description = "Transition order from SHIPPED to DELIVERED, deducting stock")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Order delivered successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid transition"),
        @ApiResponse(responseCode = "403", description = "Insufficient permissions"),
        @ApiResponse(responseCode = "404", description = "Order not found")
    })
    public ResponseEntity<OrderResponse> deliverOrder(
            @PathVariable String id,
            @Valid @RequestBody(required = false) TransitionRequest request,
            @AuthenticationPrincipal User currentUser) {

        return ResponseEntity.ok(orderService.deliverOrder(id, request != null ? request : new TransitionRequest(), currentUser.getId()));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Operation(summary = "Cancel order", description = "Cancel a SUBMITTED or APPROVED order, releasing stock")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Order cancelled successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid transition"),
        @ApiResponse(responseCode = "403", description = "Insufficient permissions"),
        @ApiResponse(responseCode = "404", description = "Order not found")
    })
    public ResponseEntity<OrderResponse> cancelOrder(
            @PathVariable String id,
            @Valid @RequestBody(required = false) TransitionRequest request,
            @AuthenticationPrincipal User currentUser) {

        return ResponseEntity.ok(orderService.cancelOrder(id, request != null ? request : new TransitionRequest(), currentUser.getId()));
    }
    
    @GetMapping("/{id}/history")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'WAREHOUSE_STAFF', 'VIEWER')")
    @Operation(summary = "Get order history", description = "Get the status transition history for an order")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "History retrieved successfully"),
        @ApiResponse(responseCode = "404", description = "Order not found")
    })
    public ResponseEntity<List<OrderStatusHistoryResponse>> getOrderHistory(@PathVariable String id) {
        orderService.getOrder(id);
        return ResponseEntity.ok(historyService.getOrderHistory(id));
    }
}
