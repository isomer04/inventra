package com.inventra.api.order;

import com.inventra.api.audit.AuditEventType;
import com.inventra.api.audit.AuditPayload;
import com.inventra.api.audit.AuditService;
import com.inventra.api.customer.CustomerService;
import com.inventra.api.entity.*;
import com.inventra.api.exception.*;
import com.inventra.api.inventory.StockMovementService;
import com.inventra.api.order.dto.*;
import com.inventra.api.product.ProductService;
import com.inventra.api.tenant.TenantContext;
import com.inventra.api.user.UserService;
import com.inventra.api.util.LogSanitizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderStatusHistoryRepository orderStatusHistoryRepository;
    private final OrderMapper orderMapper;
    private final OrderNumberGenerator orderNumberGenerator;
    private final OrderStatusHistoryService historyService;
    private final CustomerService customerService;
    private final ProductService productService;
    private final UserService userService;
    private final StockMovementService stockMovementService;
    private final AuditService auditService;

    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED_TRANSITIONS = Map.of(
            OrderStatus.DRAFT,     Set.of(OrderStatus.SUBMITTED),
            OrderStatus.SUBMITTED, Set.of(OrderStatus.APPROVED, OrderStatus.REJECTED, OrderStatus.CANCELLED),
            OrderStatus.APPROVED,  Set.of(OrderStatus.PICKING, OrderStatus.CANCELLED),
            OrderStatus.PICKING,   Set.of(OrderStatus.SHIPPED),
            OrderStatus.SHIPPED,   Set.of(OrderStatus.DELIVERED)
    );

    @Transactional
    public OrderResponse createOrder(CreateOrderRequest request, String userId) {
        String tenantId = TenantContext.requireTenantId();
        log.info("Creating order for customer {} by user {} in tenant {}",
                LogSanitizer.sanitize(request.getCustomerId()),
                LogSanitizer.sanitize(userId),
                LogSanitizer.sanitize(tenantId));

        Customer customer = customerService.getCustomerEntity(request.getCustomerId());
        User user = userService.getUserEntity(userId);
        String orderNumber = orderNumberGenerator.generateOrderNumber();

        Order order = Order.builder()
                .tenantId(tenantId)
                .orderNumber(orderNumber)
                .customer(customer)
                .status(OrderStatus.DRAFT)
                .totalAmount(BigDecimal.ZERO)
                .notes(request.getNotes())
                .createdBy(user)
                .build();

        addItemsToOrder(order, request.getItems());

        Order saved = orderRepository.save(order);
        historyService.createHistoryEntry(saved, null, OrderStatus.DRAFT, user, "Order created");

        log.info("Created order {} with number {}",
                LogSanitizer.sanitize(saved.getId()),
                LogSanitizer.sanitize(saved.getOrderNumber()));
        return orderMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrder(String orderId) {
        String tenantId = TenantContext.requireTenantId();
        Order order = orderRepository.findByIdAndTenantId(orderId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));
        return orderMapper.toResponse(order);
    }

    @Transactional(readOnly = true)
    public Page<OrderSummaryResponse> listOrders(Pageable pageable, OrderStatus status,
                                                  String customerId,
                                                  java.time.Instant startDate,
                                                  java.time.Instant endDate) {
        String tenantId = TenantContext.requireTenantId();
        log.debug("Listing orders for tenant {} with filters", LogSanitizer.sanitize(tenantId));
        return orderRepository.findByFilters(tenantId, status, customerId, startDate, endDate, pageable)
                .map(orderMapper::toSummaryResponse);
    }

    // Items semantics: null = no change, empty list = clear all, non-empty = replace.
    @Transactional
    public OrderResponse updateOrder(String orderId, UpdateOrderRequest request, String userId) {
        String tenantId = TenantContext.requireTenantId();
        log.info("Updating order {} by user {} in tenant {}",
                LogSanitizer.sanitize(orderId),
                LogSanitizer.sanitize(userId),
                LogSanitizer.sanitize(tenantId));

        Order order = requireEditableOrder(orderId, tenantId);

        if (request.getCustomerId() != null) {
            order.setCustomer(customerService.getCustomerEntity(request.getCustomerId()));
        }

        if (request.getItems() != null) {
            order.clearItems();
            if (!request.getItems().isEmpty()) {
                addItemsToOrder(order, request.getItems());
            }
        }

        if (request.getNotes() != null) {
            order.setNotes(request.getNotes());
        }

        Order saved = orderRepository.save(order);
        log.info("Updated order {}", LogSanitizer.sanitize(orderId));
        return orderMapper.toResponse(saved);
    }

    /**
     * Delete a DRAFT order.
     *
     * <p>The {@code order_status_history.order_id} foreign key uses
     * {@code ON DELETE RESTRICT} so history rows must be cleared first.
     */
    @Transactional
    public void deleteOrder(String orderId) {
        String tenantId = TenantContext.requireTenantId();
        log.info("Deleting order {} in tenant {}",
                LogSanitizer.sanitize(orderId),
                LogSanitizer.sanitize(tenantId));

        Order order = requireEditableOrder(orderId, tenantId);

        orderStatusHistoryRepository.deleteByOrderId(orderId);
        orderRepository.delete(order);

        log.info("Deleted order {}", LogSanitizer.sanitize(orderId));
    }

    /**
     * Submit order: DRAFT → SUBMITTED.
     * Locks prices at current product prices and reserves stock.
     */
    @Transactional
    public OrderResponse submitOrder(String orderId, TransitionRequest request, String userId) {
        String tenantId = TenantContext.requireTenantId();
        log.info("Submitting order {} by user {} in tenant {}",
                LogSanitizer.sanitize(orderId),
                LogSanitizer.sanitize(userId),
                LogSanitizer.sanitize(tenantId));

        Order order = requireOrder(orderId, tenantId);
        validateTransition(order.getStatus(), OrderStatus.SUBMITTED);

        if (order.getItems().isEmpty()) {
            throw new EmptyOrderException(orderId);
        }

        lockPricesAndValidateProducts(order);
        order.calculateTotalAmount();
        reserveStockForItems(order, userId);

        OrderStatus oldStatus = order.getStatus();
        order.setStatus(OrderStatus.SUBMITTED);
        Order saved = orderRepository.save(order);

        User user = userService.getUserEntity(userId);
        historyService.createHistoryEntry(saved, oldStatus, OrderStatus.SUBMITTED, user, request.getNotes());
        auditService.record(userId, user.getEmail(), AuditEventType.ORDER_SUBMITTED.toString(), "Order", orderId,
                buildStatusJson(oldStatus),
                buildSubmittedJson(saved.getTotalAmount()));

        log.info("Submitted order {} with total amount {}",
                LogSanitizer.sanitize(orderId), saved.getTotalAmount());
        return orderMapper.toResponse(saved);
    }

    /** Approve order: SUBMITTED → APPROVED. */
    @Transactional
    public OrderResponse approveOrder(String orderId, TransitionRequest request, String userId) {
        return simpleTransition(orderId, OrderStatus.APPROVED, request, userId, AuditEventType.ORDER_APPROVED.toString());
    }

    /** Reject order: SUBMITTED → REJECTED. Releases stock reservation. */
    @Transactional
    public OrderResponse rejectOrder(String orderId, TransitionRequest request, String userId) {
        String tenantId = TenantContext.requireTenantId();
        log.info("Rejecting order {} by user {} in tenant {}",
                LogSanitizer.sanitize(orderId),
                LogSanitizer.sanitize(userId),
                LogSanitizer.sanitize(tenantId));

        Order order = requireOrder(orderId, tenantId);
        validateTransition(order.getStatus(), OrderStatus.REJECTED);
        releaseStockForItems(order, userId);

        return applyTransitionAndAudit(order, OrderStatus.REJECTED, request, userId, AuditEventType.ORDER_REJECTED.toString());
    }

    /** Start picking: APPROVED → PICKING. */
    @Transactional
    public OrderResponse startPicking(String orderId, TransitionRequest request, String userId) {
        return simpleTransition(orderId, OrderStatus.PICKING, request, userId, AuditEventType.ORDER_PICKING_STARTED.toString());
    }

    /** Ship order: PICKING → SHIPPED. */
    @Transactional
    public OrderResponse shipOrder(String orderId, TransitionRequest request, String userId) {
        return simpleTransition(orderId, OrderStatus.SHIPPED, request, userId, AuditEventType.ORDER_SHIPPED.toString());
    }

    /** Deliver order: SHIPPED → DELIVERED. Deducts stock. */
    @Transactional
    public OrderResponse deliverOrder(String orderId, TransitionRequest request, String userId) {
        String tenantId = TenantContext.requireTenantId();
        log.info("Delivering order {} by user {} in tenant {}",
                LogSanitizer.sanitize(orderId),
                LogSanitizer.sanitize(userId),
                LogSanitizer.sanitize(tenantId));

        Order order = requireOrder(orderId, tenantId);
        validateTransition(order.getStatus(), OrderStatus.DELIVERED);
        deductStockForItems(order, userId);

        return applyTransitionAndAudit(order, OrderStatus.DELIVERED, request, userId, AuditEventType.ORDER_DELIVERED.toString());
    }

    /** Cancel order: SUBMITTED/APPROVED → CANCELLED. Releases stock if already reserved. */
    @Transactional
    public OrderResponse cancelOrder(String orderId, TransitionRequest request, String userId) {
        String tenantId = TenantContext.requireTenantId();
        log.info("Cancelling order {} by user {} in tenant {}",
                LogSanitizer.sanitize(orderId),
                LogSanitizer.sanitize(userId),
                LogSanitizer.sanitize(tenantId));

        Order order = requireOrder(orderId, tenantId);
        validateTransition(order.getStatus(), OrderStatus.CANCELLED);

        boolean stockWasReserved = order.getStatus() == OrderStatus.SUBMITTED
                || order.getStatus() == OrderStatus.APPROVED;
        if (stockWasReserved) {
            releaseStockForItems(order, userId);
        }

        return applyTransitionAndAudit(order, OrderStatus.CANCELLED, request, userId, AuditEventType.ORDER_CANCELLED.toString());
    }

    private Order requireOrder(String orderId, String tenantId) {
        return orderRepository.findByIdAndTenantId(orderId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));
    }

    private Order requireEditableOrder(String orderId, String tenantId) {
        Order order = requireOrder(orderId, tenantId);
        if (!order.isEditable()) {
            throw new OrderNotEditableException(orderId, order.getStatus());
        }
        return order;
    }

    /**
     * Handles transitions with no stock side-effects (APPROVED, PICKING, SHIPPED).
     *
     * <p>These write an audit record like every other transition. They previously wrote
     * only status history, which left the audit log unable to answer "who approved this
     * order" — arguably the most authorization-sensitive transition, since approval commits
     * the business to fulfilment. Audit coverage across transitions is now uniform.
     */
    private OrderResponse simpleTransition(String orderId, OrderStatus toStatus,
                                           TransitionRequest request, String userId,
                                           String auditAction) {
        String tenantId = TenantContext.requireTenantId();
        log.info("Transitioning order {} to {} by user {} in tenant {}",
                LogSanitizer.sanitize(orderId),
                LogSanitizer.sanitize(toStatus),
                LogSanitizer.sanitize(userId),
                LogSanitizer.sanitize(tenantId));

        Order order = requireOrder(orderId, tenantId);
        validateTransition(order.getStatus(), toStatus);

        return applyTransitionAndAudit(order, toStatus, request, userId, auditAction);
    }

    /**
     * Applies a status change, records history, and writes an audit entry.
     * Used by transitions that have already handled their stock side-effects.
     */
    private OrderResponse applyTransitionAndAudit(Order order, OrderStatus toStatus,
                                                   TransitionRequest request, String userId,
                                                   String auditAction) {
        OrderStatus oldStatus = order.getStatus();
        order.setStatus(toStatus);
        Order saved = orderRepository.save(order);

        User user = userService.getUserEntity(userId);
        historyService.createHistoryEntry(saved, oldStatus, toStatus, user, request.getNotes());
        auditService.record(userId, user.getEmail(), auditAction, "Order", saved.getId(),
                buildStatusJson(oldStatus),
                buildStatusJson(toStatus));

        log.info("Transitioned order {} from {} to {}",
                LogSanitizer.sanitize(saved.getId()),
                LogSanitizer.sanitize(oldStatus),
                LogSanitizer.sanitize(toStatus));
        return orderMapper.toResponse(saved);
    }

    private void validateTransition(OrderStatus from, OrderStatus to) {
        Set<OrderStatus> allowed = ALLOWED_TRANSITIONS.get(from);
        if (allowed == null || !allowed.contains(to)) {
            throw new InvalidOrderTransitionException(from, to);
        }
    }

    /**
     * Builds {@link OrderItem}s from the request list and attaches them to the order.
     * Fetches and validates each product exactly once via a product map.
     */
    private void addItemsToOrder(Order order, List<CreateOrderItemRequest> itemRequests) {
        List<Product> products = validateAndGetProducts(itemRequests);
        Map<String, Product> productById = new HashMap<>();
        products.forEach(p -> productById.put(p.getId(), p));

        for (CreateOrderItemRequest itemRequest : itemRequests) {
            Product product = productById.get(itemRequest.getProductId());
            OrderItem item = OrderItem.builder()
                    .product(product)
                    .quantity(itemRequest.getQuantity())
                    .unitPrice(BigDecimal.ZERO)   // priced on submit
                    .totalPrice(BigDecimal.ZERO)
                    .build();
            order.addItem(item);
        }
    }

    /**
     * Locks each item's unit price to the current product price and validates
     * that every product is ACTIVE. Called during order submission.
     */
    private void lockPricesAndValidateProducts(Order order) {
        for (OrderItem item : order.getItems()) {
            Product product = item.getProduct();
            if (product.getStatus() != ProductStatus.ACTIVE) {
                throw new InvalidOrderTransitionException(
                        String.format("Product %s (%s) is not active", product.getName(), product.getSku()));
            }
            item.setUnitPrice(product.getUnitPrice());
            item.calculateTotalPrice();
        }
    }

    private List<Product> validateAndGetProducts(List<CreateOrderItemRequest> items) {
        List<String> productIds = items.stream()
                .map(CreateOrderItemRequest::getProductId)
                .distinct()
                .toList();

        // uq_order_item_product UNIQUE (order_id, product_id) rejects repeated products,
        // so two lines naming the same product used to surface as a 500 at flush time.
        // The .distinct() above only deduplicates the product *lookup*; the item loop in
        // addItemsToOrder still creates one row per request entry. Fail here with a 400
        // that names the problem instead of letting the constraint speak for us.
        if (productIds.size() != items.size()) {
            throw new InvalidRequestException(
                    "An order cannot list the same product twice. Combine the quantities into one line.");
        }

        List<Product> products = new ArrayList<>();
        for (String productId : productIds) {
            products.add(productService.getProductEntity(productId));
        }
        return products;
    }

    private void reserveStockForItems(Order order, String userId) {
        for (OrderItem item : order.getItems()) {
            stockMovementService.reserveStock(
                    item.getProduct().getId(), item.getQuantity(), order.getId(), userId);
        }
    }

    private void releaseStockForItems(Order order, String userId) {
        for (OrderItem item : order.getItems()) {
            stockMovementService.releaseReservation(
                    item.getProduct().getId(), item.getQuantity(), order.getId(), userId);
        }
    }

    private void deductStockForItems(Order order, String userId) {
        for (OrderItem item : order.getItems()) {
            stockMovementService.deductStock(
                    item.getProduct().getId(), item.getQuantity(), order.getId(), userId);
        }
    }

    // -------------------------------------------------------------------------
    // Audit JSON helpers — avoids fragile string concatenation at call sites
    // -------------------------------------------------------------------------

    private static String buildStatusJson(OrderStatus status) {
        return AuditPayload.of("status", status);
    }

    private static String buildSubmittedJson(java.math.BigDecimal total) {
        return AuditPayload.of("status", OrderStatus.SUBMITTED, "total", total);
    }
}
