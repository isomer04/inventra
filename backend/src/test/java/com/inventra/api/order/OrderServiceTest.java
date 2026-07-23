package com.inventra.api.order;

import com.inventra.api.audit.AuditService;
import com.inventra.api.customer.CustomerService;
import com.inventra.api.entity.*;
import com.inventra.api.exception.*;
import com.inventra.api.inventory.StockMovementService;
import com.inventra.api.order.dto.*;
import com.inventra.api.product.ProductService;
import com.inventra.api.tenant.TenantContext;
import com.inventra.api.user.UserService;
import com.inventra.api.order.OrderStatusHistoryRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderItemRepository orderItemRepository;

    @Mock
    private OrderStatusHistoryRepository orderStatusHistoryRepository;

    @Mock
    private OrderMapper orderMapper;

    @Mock
    private OrderNumberGenerator orderNumberGenerator;

    @Mock
    private OrderStatusHistoryService historyService;

    @Mock
    private CustomerService customerService;

    @Mock
    private ProductService productService;

    @Mock
    private UserService userService;

    @Mock
    private StockMovementService stockMovementService;

    @Mock
    private AuditService auditService;

    @InjectMocks
    private OrderService orderService;

    private static final String TENANT_ID = "tenant-123";
    private static final String USER_ID = "user-123";
    private static final String ORDER_ID = "order-123";
    private static final String CUSTOMER_ID = "customer-123";
    private static final String PRODUCT_ID = "product-123";

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(TENANT_ID);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void createOrder_whenValidInput_thenCreatesOrderInDraftStatus() {
        CreateOrderRequest request = new CreateOrderRequest();
        request.setCustomerId(CUSTOMER_ID);
        request.setNotes("Test order");
        CreateOrderItemRequest itemRequest = new CreateOrderItemRequest();
        itemRequest.setProductId(PRODUCT_ID);
        itemRequest.setQuantity(5);
        request.setItems(List.of(itemRequest));

        Customer customer = Customer.builder()
                .id(CUSTOMER_ID)
                .tenantId(TENANT_ID)
                .name("Test Customer")
                .build();

        Product product = Product.builder()
                .id(PRODUCT_ID)
                .tenant(Tenant.builder().id(TENANT_ID).build())
                .sku("SKU-001")
                .name("Test Product")
                .unitPrice(new BigDecimal("10.00"))
                .status(ProductStatus.ACTIVE)
                .build();

        User user = User.builder()
                .id(USER_ID)
                .tenant(Tenant.builder().id(TENANT_ID).build())
                .email("user@test.com")
                .build();

        Order savedOrder = Order.builder()
                .id(ORDER_ID)
                .tenantId(TENANT_ID)
                .orderNumber("ORD-001")
                .customer(customer)
                .status(OrderStatus.DRAFT)
                .totalAmount(BigDecimal.ZERO)
                .createdBy(user)
                .build();

        OrderResponse expectedResponse = new OrderResponse();
        expectedResponse.setId(ORDER_ID);
        expectedResponse.setStatus(OrderStatus.DRAFT);

        when(customerService.getCustomerEntity(CUSTOMER_ID)).thenReturn(customer);
        when(productService.getProductEntity(PRODUCT_ID)).thenReturn(product);
        when(userService.getUserEntity(USER_ID)).thenReturn(user);
        when(orderNumberGenerator.generateOrderNumber()).thenReturn("ORD-001");
        when(orderRepository.save(any(Order.class))).thenReturn(savedOrder);
        when(orderMapper.toResponse(savedOrder)).thenReturn(expectedResponse);

        OrderResponse result = orderService.createOrder(request, USER_ID);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(ORDER_ID);
        assertThat(result.getStatus()).isEqualTo(OrderStatus.DRAFT);

        verify(orderRepository).save(any(Order.class));
        verify(historyService).createHistoryEntry(eq(savedOrder), isNull(), eq(OrderStatus.DRAFT), eq(user), eq("Order created"));
    }

    @Test
    void createOrder_whenCustomerNotFound_thenThrowsResourceNotFoundException() {
        CreateOrderRequest request = new CreateOrderRequest();
        request.setCustomerId(CUSTOMER_ID);
        request.setItems(List.of());

        when(customerService.getCustomerEntity(CUSTOMER_ID))
                .thenThrow(new ResourceNotFoundException("Customer not found"));

        assertThatThrownBy(() -> orderService.createOrder(request, USER_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Customer not found");

        verify(orderRepository, never()).save(any());
    }

    @Test
    void createOrder_whenProductNotFound_thenThrowsResourceNotFoundException() {
        CreateOrderRequest request = new CreateOrderRequest();
        request.setCustomerId(CUSTOMER_ID);
        CreateOrderItemRequest itemRequest = new CreateOrderItemRequest();
        itemRequest.setProductId(PRODUCT_ID);
        itemRequest.setQuantity(5);
        request.setItems(List.of(itemRequest));

        Customer customer = Customer.builder().id(CUSTOMER_ID).build();

        when(customerService.getCustomerEntity(CUSTOMER_ID)).thenReturn(customer);
        when(productService.getProductEntity(PRODUCT_ID))
                .thenThrow(new ResourceNotFoundException("Product not found"));

        assertThatThrownBy(() -> orderService.createOrder(request, USER_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Product not found");

        verify(orderRepository, never()).save(any());
    }

    @Test
    void submitOrder_whenValidDraftOrder_thenTransitionsToSubmittedAndReservesStock() {
        TransitionRequest request = new TransitionRequest();
        request.setNotes("Submitting order");

        Product product = Product.builder()
                .id(PRODUCT_ID)
                .sku("SKU-001")
                .name("Test Product")
                .unitPrice(new BigDecimal("10.00"))
                .status(ProductStatus.ACTIVE)
                .build();

        OrderItem item = OrderItem.builder()
                .product(product)
                .quantity(5)
                .unitPrice(BigDecimal.ZERO)
                .totalPrice(BigDecimal.ZERO)
                .build();

        Order order = Order.builder()
                .id(ORDER_ID)
                .tenantId(TENANT_ID)
                .orderNumber("ORD-001")
                .status(OrderStatus.DRAFT)
                .totalAmount(BigDecimal.ZERO)
                .build();
        order.addItem(item);

        User user = User.builder()
                .id(USER_ID)
                .email("user@test.com")
                .build();

        Order submittedOrder = Order.builder()
                .id(ORDER_ID)
                .tenantId(TENANT_ID)
                .orderNumber("ORD-001")
                .status(OrderStatus.SUBMITTED)
                .totalAmount(new BigDecimal("50.00"))
                .build();

        OrderResponse expectedResponse = new OrderResponse();
        expectedResponse.setId(ORDER_ID);
        expectedResponse.setStatus(OrderStatus.SUBMITTED);

        when(orderRepository.findByIdAndTenantId(ORDER_ID, TENANT_ID)).thenReturn(Optional.of(order));
        when(userService.getUserEntity(USER_ID)).thenReturn(user);
        when(orderRepository.save(any(Order.class))).thenReturn(submittedOrder);
        when(orderMapper.toResponse(submittedOrder)).thenReturn(expectedResponse);

        OrderResponse result = orderService.submitOrder(ORDER_ID, request, USER_ID);

        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(OrderStatus.SUBMITTED);

        verify(stockMovementService).reserveStock(eq(PRODUCT_ID), eq(5), eq(ORDER_ID), eq(USER_ID));
        verify(historyService).createHistoryEntry(eq(submittedOrder), eq(OrderStatus.DRAFT), eq(OrderStatus.SUBMITTED), eq(user), eq("Submitting order"));
        verify(auditService).record(eq(USER_ID), eq("user@test.com"), eq("ORDER_SUBMITTED"), eq("Order"), eq(ORDER_ID), anyString(), anyString());
    }

    @Test
    void submitOrder_whenOrderIsEmpty_thenThrowsEmptyOrderException() {
        TransitionRequest request = new TransitionRequest();

        Order order = Order.builder()
                .id(ORDER_ID)
                .tenantId(TENANT_ID)
                .status(OrderStatus.DRAFT)
                .build();

        when(orderRepository.findByIdAndTenantId(ORDER_ID, TENANT_ID)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.submitOrder(ORDER_ID, request, USER_ID))
                .isInstanceOf(EmptyOrderException.class);

        verify(stockMovementService, never()).reserveStock(anyString(), anyInt(), anyString(), anyString());
        verify(orderRepository, never()).save(any());
    }

    @Test
    void submitOrder_whenProductIsInactive_thenThrowsInvalidOrderTransitionException() {
        TransitionRequest request = new TransitionRequest();

        Product product = Product.builder()
                .id(PRODUCT_ID)
                .sku("SKU-001")
                .name("Test Product")
                .unitPrice(new BigDecimal("10.00"))
                .status(ProductStatus.DISCONTINUED)
                .build();

        OrderItem item = OrderItem.builder()
                .product(product)
                .quantity(5)
                .build();

        Order order = Order.builder()
                .id(ORDER_ID)
                .tenantId(TENANT_ID)
                .status(OrderStatus.DRAFT)
                .build();
        order.addItem(item);

        when(orderRepository.findByIdAndTenantId(ORDER_ID, TENANT_ID)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.submitOrder(ORDER_ID, request, USER_ID))
                .isInstanceOf(InvalidOrderTransitionException.class)
                .hasMessageContaining("is not active");

        verify(stockMovementService, never()).reserveStock(anyString(), anyInt(), anyString(), anyString());
    }

    @Test
    void submitOrder_whenInvalidTransition_thenThrowsInvalidOrderTransitionException() {
        TransitionRequest request = new TransitionRequest();

        Order order = Order.builder()
                .id(ORDER_ID)
                .tenantId(TENANT_ID)
                .status(OrderStatus.DELIVERED)
                .build();

        when(orderRepository.findByIdAndTenantId(ORDER_ID, TENANT_ID)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.submitOrder(ORDER_ID, request, USER_ID))
                .isInstanceOf(InvalidOrderTransitionException.class);

        verify(orderRepository, never()).save(any());
    }

    @Test
    void rejectOrder_whenValidSubmittedOrder_thenTransitionsToRejectedAndReleasesStock() {
        TransitionRequest request = new TransitionRequest();
        request.setNotes("Rejecting order");

        Product product = Product.builder()
                .id(PRODUCT_ID)
                .build();

        OrderItem item = OrderItem.builder()
                .product(product)
                .quantity(5)
                .build();

        Order order = Order.builder()
                .id(ORDER_ID)
                .tenantId(TENANT_ID)
                .status(OrderStatus.SUBMITTED)
                .build();
        order.addItem(item);

        User user = User.builder()
                .id(USER_ID)
                .email("user@test.com")
                .build();

        Order rejectedOrder = Order.builder()
                .id(ORDER_ID)
                .tenantId(TENANT_ID)
                .status(OrderStatus.REJECTED)
                .build();

        OrderResponse expectedResponse = new OrderResponse();
        expectedResponse.setStatus(OrderStatus.REJECTED);

        when(orderRepository.findByIdAndTenantId(ORDER_ID, TENANT_ID)).thenReturn(Optional.of(order));
        when(userService.getUserEntity(USER_ID)).thenReturn(user);
        when(orderRepository.save(any(Order.class))).thenReturn(rejectedOrder);
        when(orderMapper.toResponse(rejectedOrder)).thenReturn(expectedResponse);

        OrderResponse result = orderService.rejectOrder(ORDER_ID, request, USER_ID);

        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(OrderStatus.REJECTED);

        verify(stockMovementService).releaseReservation(eq(PRODUCT_ID), eq(5), eq(ORDER_ID), eq(USER_ID));
        verify(historyService).createHistoryEntry(eq(rejectedOrder), eq(OrderStatus.SUBMITTED), eq(OrderStatus.REJECTED), eq(user), eq("Rejecting order"));
        verify(auditService).record(eq(USER_ID), eq("user@test.com"), eq("ORDER_REJECTED"), eq("Order"), eq(ORDER_ID), anyString(), anyString());
    }

    @Test
    void cancelOrder_whenSubmittedOrder_thenTransitionsToCancelledAndReleasesStock() {
        TransitionRequest request = new TransitionRequest();

        Product product = Product.builder()
                .id(PRODUCT_ID)
                .build();

        OrderItem item = OrderItem.builder()
                .product(product)
                .quantity(5)
                .build();

        Order order = Order.builder()
                .id(ORDER_ID)
                .tenantId(TENANT_ID)
                .status(OrderStatus.SUBMITTED)
                .build();
        order.addItem(item);

        User user = User.builder()
                .id(USER_ID)
                .email("user@test.com")
                .build();

        Order cancelledOrder = Order.builder()
                .id(ORDER_ID)
                .tenantId(TENANT_ID)
                .status(OrderStatus.CANCELLED)
                .build();

        OrderResponse expectedResponse = new OrderResponse();
        expectedResponse.setStatus(OrderStatus.CANCELLED);

        when(orderRepository.findByIdAndTenantId(ORDER_ID, TENANT_ID)).thenReturn(Optional.of(order));
        when(userService.getUserEntity(USER_ID)).thenReturn(user);
        when(orderRepository.save(any(Order.class))).thenReturn(cancelledOrder);
        when(orderMapper.toResponse(cancelledOrder)).thenReturn(expectedResponse);

        OrderResponse result = orderService.cancelOrder(ORDER_ID, request, USER_ID);

        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(OrderStatus.CANCELLED);

        verify(stockMovementService).releaseReservation(eq(PRODUCT_ID), eq(5), eq(ORDER_ID), eq(USER_ID));
        verify(auditService).record(eq(USER_ID), eq("user@test.com"), eq("ORDER_CANCELLED"), eq("Order"), eq(ORDER_ID), anyString(), anyString());
    }

    @Test
    void cancelOrder_whenApprovedOrder_thenTransitionsToCancelledAndReleasesStock() {
        TransitionRequest request = new TransitionRequest();

        Product product = Product.builder()
                .id(PRODUCT_ID)
                .build();

        OrderItem item = OrderItem.builder()
                .product(product)
                .quantity(5)
                .build();

        Order order = Order.builder()
                .id(ORDER_ID)
                .tenantId(TENANT_ID)
                .status(OrderStatus.APPROVED)
                .build();
        order.addItem(item);

        User user = User.builder()
                .id(USER_ID)
                .email("user@test.com")
                .build();

        Order cancelledOrder = Order.builder()
                .id(ORDER_ID)
                .tenantId(TENANT_ID)
                .status(OrderStatus.CANCELLED)
                .build();

        OrderResponse expectedResponse = new OrderResponse();
        expectedResponse.setStatus(OrderStatus.CANCELLED);

        when(orderRepository.findByIdAndTenantId(ORDER_ID, TENANT_ID)).thenReturn(Optional.of(order));
        when(userService.getUserEntity(USER_ID)).thenReturn(user);
        when(orderRepository.save(any(Order.class))).thenReturn(cancelledOrder);
        when(orderMapper.toResponse(cancelledOrder)).thenReturn(expectedResponse);

        OrderResponse result = orderService.cancelOrder(ORDER_ID, request, USER_ID);

        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(OrderStatus.CANCELLED);

        verify(stockMovementService).releaseReservation(eq(PRODUCT_ID), eq(5), eq(ORDER_ID), eq(USER_ID));
    }

    @Test
    void cancelOrder_whenInvalidTransition_thenThrowsInvalidOrderTransitionException() {
        TransitionRequest request = new TransitionRequest();

        Order order = Order.builder()
                .id(ORDER_ID)
                .tenantId(TENANT_ID)
                .status(OrderStatus.DELIVERED)
                .build();

        when(orderRepository.findByIdAndTenantId(ORDER_ID, TENANT_ID)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.cancelOrder(ORDER_ID, request, USER_ID))
                .isInstanceOf(InvalidOrderTransitionException.class);

        verify(stockMovementService, never()).releaseReservation(anyString(), anyInt(), anyString(), anyString());
    }

    @Test
    void deliverOrder_whenValidShippedOrder_thenTransitionsToDeliveredAndDeductsStock() {
        TransitionRequest request = new TransitionRequest();

        Product product = Product.builder()
                .id(PRODUCT_ID)
                .build();

        OrderItem item = OrderItem.builder()
                .product(product)
                .quantity(5)
                .build();

        Order order = Order.builder()
                .id(ORDER_ID)
                .tenantId(TENANT_ID)
                .status(OrderStatus.SHIPPED)
                .build();
        order.addItem(item);

        User user = User.builder()
                .id(USER_ID)
                .email("user@test.com")
                .build();

        Order deliveredOrder = Order.builder()
                .id(ORDER_ID)
                .tenantId(TENANT_ID)
                .status(OrderStatus.DELIVERED)
                .build();

        OrderResponse expectedResponse = new OrderResponse();
        expectedResponse.setStatus(OrderStatus.DELIVERED);

        when(orderRepository.findByIdAndTenantId(ORDER_ID, TENANT_ID)).thenReturn(Optional.of(order));
        when(userService.getUserEntity(USER_ID)).thenReturn(user);
        when(orderRepository.save(any(Order.class))).thenReturn(deliveredOrder);
        when(orderMapper.toResponse(deliveredOrder)).thenReturn(expectedResponse);

        OrderResponse result = orderService.deliverOrder(ORDER_ID, request, USER_ID);

        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(OrderStatus.DELIVERED);

        verify(stockMovementService).deductStock(eq(PRODUCT_ID), eq(5), eq(ORDER_ID), eq(USER_ID));
        verify(historyService).createHistoryEntry(eq(deliveredOrder), eq(OrderStatus.SHIPPED), eq(OrderStatus.DELIVERED), eq(user), isNull());
        verify(auditService).record(eq(USER_ID), eq("user@test.com"), eq("ORDER_DELIVERED"), eq("Order"), eq(ORDER_ID), anyString(), anyString());
    }

    @Test
    void updateOrder_whenDraftOrder_thenUpdatesSuccessfully() {
        UpdateOrderRequest request = new UpdateOrderRequest();
        request.setNotes("Updated notes");

        Order order = Order.builder()
                .id(ORDER_ID)
                .tenantId(TENANT_ID)
                .status(OrderStatus.DRAFT)
                .notes("Original notes")
                .build();

        Order updatedOrder = Order.builder()
                .id(ORDER_ID)
                .tenantId(TENANT_ID)
                .status(OrderStatus.DRAFT)
                .notes("Updated notes")
                .build();

        OrderResponse expectedResponse = new OrderResponse();
        expectedResponse.setNotes("Updated notes");

        when(orderRepository.findByIdAndTenantId(ORDER_ID, TENANT_ID)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenReturn(updatedOrder);
        when(orderMapper.toResponse(updatedOrder)).thenReturn(expectedResponse);

        OrderResponse result = orderService.updateOrder(ORDER_ID, request, USER_ID);

        assertThat(result).isNotNull();
        assertThat(result.getNotes()).isEqualTo("Updated notes");

        verify(orderRepository).save(any(Order.class));
    }

    @Test
    void updateOrder_whenOrderNotEditable_thenThrowsOrderNotEditableException() {
        UpdateOrderRequest request = new UpdateOrderRequest();

        Order order = Order.builder()
                .id(ORDER_ID)
                .tenantId(TENANT_ID)
                .status(OrderStatus.SUBMITTED)
                .build();

        when(orderRepository.findByIdAndTenantId(ORDER_ID, TENANT_ID)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.updateOrder(ORDER_ID, request, USER_ID))
                .isInstanceOf(OrderNotEditableException.class);

        verify(orderRepository, never()).save(any());
    }

    @Test
    void deleteOrder_whenDraftOrder_thenDeletesSuccessfully() {
        Order order = Order.builder()
                .id(ORDER_ID)
                .tenantId(TENANT_ID)
                .status(OrderStatus.DRAFT)
                .build();

        when(orderRepository.findByIdAndTenantId(ORDER_ID, TENANT_ID)).thenReturn(Optional.of(order));

        orderService.deleteOrder(ORDER_ID);

        verify(orderRepository).delete(order);
    }

    @Test
    void deleteOrder_whenOrderNotEditable_thenThrowsOrderNotEditableException() {
        Order order = Order.builder()
                .id(ORDER_ID)
                .tenantId(TENANT_ID)
                .status(OrderStatus.SUBMITTED)
                .build();

        when(orderRepository.findByIdAndTenantId(ORDER_ID, TENANT_ID)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.deleteOrder(ORDER_ID))
                .isInstanceOf(OrderNotEditableException.class);

        verify(orderRepository, never()).delete(any());
    }

    @Test
    void getOrder_whenOrderExists_thenReturnsOrder() {
        Order order = Order.builder()
                .id(ORDER_ID)
                .tenantId(TENANT_ID)
                .build();

        OrderResponse expectedResponse = new OrderResponse();
        expectedResponse.setId(ORDER_ID);

        when(orderRepository.findByIdAndTenantId(ORDER_ID, TENANT_ID)).thenReturn(Optional.of(order));
        when(orderMapper.toResponse(order)).thenReturn(expectedResponse);

        OrderResponse result = orderService.getOrder(ORDER_ID);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(ORDER_ID);
    }

    @Test
    void getOrder_whenOrderNotFound_thenThrowsResourceNotFoundException() {
        when(orderRepository.findByIdAndTenantId(ORDER_ID, TENANT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.getOrder(ORDER_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Order not found");
    }

    @Test
    void approveOrder_whenValidSubmittedOrder_thenTransitionsToApproved() {
        TransitionRequest request = new TransitionRequest();

        Order order = Order.builder()
                .id(ORDER_ID)
                .tenantId(TENANT_ID)
                .status(OrderStatus.SUBMITTED)
                .build();

        User user = User.builder()
                .id(USER_ID)
                .build();

        Order approvedOrder = Order.builder()
                .id(ORDER_ID)
                .tenantId(TENANT_ID)
                .status(OrderStatus.APPROVED)
                .build();

        OrderResponse expectedResponse = new OrderResponse();
        expectedResponse.setStatus(OrderStatus.APPROVED);

        when(orderRepository.findByIdAndTenantId(ORDER_ID, TENANT_ID)).thenReturn(Optional.of(order));
        when(userService.getUserEntity(USER_ID)).thenReturn(user);
        when(orderRepository.save(any(Order.class))).thenReturn(approvedOrder);
        when(orderMapper.toResponse(approvedOrder)).thenReturn(expectedResponse);

        OrderResponse result = orderService.approveOrder(ORDER_ID, request, USER_ID);

        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(OrderStatus.APPROVED);
    }

    @Test
    void startPicking_whenValidApprovedOrder_thenTransitionsToPicking() {
        TransitionRequest request = new TransitionRequest();

        Order order = Order.builder()
                .id(ORDER_ID)
                .tenantId(TENANT_ID)
                .status(OrderStatus.APPROVED)
                .build();

        User user = User.builder()
                .id(USER_ID)
                .build();

        Order pickingOrder = Order.builder()
                .id(ORDER_ID)
                .tenantId(TENANT_ID)
                .status(OrderStatus.PICKING)
                .build();

        OrderResponse expectedResponse = new OrderResponse();
        expectedResponse.setStatus(OrderStatus.PICKING);

        when(orderRepository.findByIdAndTenantId(ORDER_ID, TENANT_ID)).thenReturn(Optional.of(order));
        when(userService.getUserEntity(USER_ID)).thenReturn(user);
        when(orderRepository.save(any(Order.class))).thenReturn(pickingOrder);
        when(orderMapper.toResponse(pickingOrder)).thenReturn(expectedResponse);

        OrderResponse result = orderService.startPicking(ORDER_ID, request, USER_ID);

        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(OrderStatus.PICKING);
    }

    @Test
    void shipOrder_whenValidPickingOrder_thenTransitionsToShipped() {
        TransitionRequest request = new TransitionRequest();

        Order order = Order.builder()
                .id(ORDER_ID)
                .tenantId(TENANT_ID)
                .status(OrderStatus.PICKING)
                .build();

        User user = User.builder()
                .id(USER_ID)
                .build();

        Order shippedOrder = Order.builder()
                .id(ORDER_ID)
                .tenantId(TENANT_ID)
                .status(OrderStatus.SHIPPED)
                .build();

        OrderResponse expectedResponse = new OrderResponse();
        expectedResponse.setStatus(OrderStatus.SHIPPED);

        when(orderRepository.findByIdAndTenantId(ORDER_ID, TENANT_ID)).thenReturn(Optional.of(order));
        when(userService.getUserEntity(USER_ID)).thenReturn(user);
        when(orderRepository.save(any(Order.class))).thenReturn(shippedOrder);
        when(orderMapper.toResponse(shippedOrder)).thenReturn(expectedResponse);

        OrderResponse result = orderService.shipOrder(ORDER_ID, request, USER_ID);

        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(OrderStatus.SHIPPED);
    }
}
