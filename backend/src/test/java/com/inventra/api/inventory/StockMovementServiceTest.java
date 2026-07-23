package com.inventra.api.inventory;

import com.inventra.api.entity.InventoryItem;
import com.inventra.api.entity.MovementType;
import com.inventra.api.entity.StockMovement;
import com.inventra.api.exception.ConcurrentUpdateException;
import com.inventra.api.exception.InsufficientStockException;
import com.inventra.api.exception.InvalidQuantityException;
import com.inventra.api.inventory.dto.StockMovementResponse;
import com.inventra.api.repository.InventoryItemRepository;
import com.inventra.api.repository.StockMovementRepository;
import com.inventra.api.tenant.TenantContext;
import jakarta.persistence.OptimisticLockException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StockMovementServiceTest {

    @Mock
    private StockMovementRepository stockMovementRepository;

    @Mock
    private InventoryItemRepository inventoryItemRepository;

    @Mock
    private InventoryService inventoryService;

    @Mock
    private StockMovementMapper stockMovementMapper;

    @InjectMocks
    private StockMovementService stockMovementService;

    private static final String TENANT_ID = "tenant-123";
    private static final String PRODUCT_ID = "product-123";
    private static final String USER_ID = "user-123";
    private static final String REFERENCE_ID = "order-123";

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(TENANT_ID);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void receiveStock_whenValidQuantity_thenIncreasesOnHandAndCreatesMovement() {
        InventoryItem inventoryItem = InventoryItem.builder()
                .tenantId(TENANT_ID)
                .productId(PRODUCT_ID)
                .quantityOnHand(100)
                .quantityReserved(20)
                .build();

        StockMovement savedMovement = StockMovement.builder()
                .id("movement-123")
                .tenantId(TENANT_ID)
                .productId(PRODUCT_ID)
                .type(MovementType.RECEIPT)
                .quantity(50)
                .build();

        StockMovementResponse expectedResponse = new StockMovementResponse();
        expectedResponse.setQuantity(50);

        when(inventoryService.getInventoryItemEntity(PRODUCT_ID)).thenReturn(inventoryItem);
        when(stockMovementRepository.save(any(StockMovement.class))).thenReturn(savedMovement);
        when(stockMovementMapper.toResponse(savedMovement)).thenReturn(expectedResponse);

        StockMovementResponse result = stockMovementService.receiveStock(PRODUCT_ID, 50, "Received shipment", USER_ID);

        assertThat(result).isNotNull();
        assertThat(result.getQuantity()).isEqualTo(50);
        assertThat(inventoryItem.getQuantityOnHand()).isEqualTo(150); // 100 + 50

        verify(inventoryItemRepository).save(inventoryItem);
        verify(stockMovementRepository).save(any(StockMovement.class));
    }

    @Test
    void receiveStock_whenZeroQuantity_thenThrowsInvalidQuantityException() {
        assertThatThrownBy(() -> stockMovementService.receiveStock(PRODUCT_ID, 0, "notes", USER_ID))
                .isInstanceOf(InvalidQuantityException.class)
                .hasMessageContaining("Receipt quantity must be greater than 0");

        verify(inventoryService, never()).getInventoryItemEntity(anyString());
    }

    @Test
    void receiveStock_whenNegativeQuantity_thenThrowsInvalidQuantityException() {
        assertThatThrownBy(() -> stockMovementService.receiveStock(PRODUCT_ID, -10, "notes", USER_ID))
                .isInstanceOf(InvalidQuantityException.class)
                .hasMessageContaining("Receipt quantity must be greater than 0");

        verify(inventoryService, never()).getInventoryItemEntity(anyString());
    }

    @Test
    void receiveStock_whenOptimisticLockException_thenThrowsConcurrentUpdateException() {
        InventoryItem inventoryItem = InventoryItem.builder()
                .quantityOnHand(100)
                .build();

        when(inventoryService.getInventoryItemEntity(PRODUCT_ID)).thenReturn(inventoryItem);
        when(inventoryItemRepository.save(any(InventoryItem.class)))
                .thenThrow(new OptimisticLockException("Concurrent update"));

        assertThatThrownBy(() -> stockMovementService.receiveStock(PRODUCT_ID, 50, "notes", USER_ID))
                .isInstanceOf(ConcurrentUpdateException.class)
                .hasMessageContaining("Concurrent update detected");
    }

    @Test
    void adjustStock_whenPositiveAdjustment_thenIncreasesOnHand() {
        InventoryItem inventoryItem = InventoryItem.builder()
                .quantityOnHand(100)
                .quantityReserved(20)
                .build();

        StockMovement savedMovement = StockMovement.builder()
                .id("movement-123")
                .type(MovementType.ADJUSTMENT)
                .quantity(25)
                .build();

        StockMovementResponse expectedResponse = new StockMovementResponse();

        when(inventoryService.getInventoryItemEntity(PRODUCT_ID)).thenReturn(inventoryItem);
        when(stockMovementRepository.save(any(StockMovement.class))).thenReturn(savedMovement);
        when(stockMovementMapper.toResponse(savedMovement)).thenReturn(expectedResponse);

        stockMovementService.adjustStock(PRODUCT_ID, 25, "Inventory count adjustment", USER_ID);

        assertThat(inventoryItem.getQuantityOnHand()).isEqualTo(125); // 100 + 25
        verify(inventoryItemRepository).save(inventoryItem);
    }

    @Test
    void adjustStock_whenNegativeAdjustment_thenDecreasesOnHand() {
        InventoryItem inventoryItem = InventoryItem.builder()
                .quantityOnHand(100)
                .quantityReserved(20)
                .build();

        StockMovement savedMovement = StockMovement.builder()
                .id("movement-123")
                .type(MovementType.ADJUSTMENT)
                .quantity(-30)
                .build();

        StockMovementResponse expectedResponse = new StockMovementResponse();

        when(inventoryService.getInventoryItemEntity(PRODUCT_ID)).thenReturn(inventoryItem);
        when(stockMovementRepository.save(any(StockMovement.class))).thenReturn(savedMovement);
        when(stockMovementMapper.toResponse(savedMovement)).thenReturn(expectedResponse);

        stockMovementService.adjustStock(PRODUCT_ID, -30, "Damaged goods", USER_ID);

        assertThat(inventoryItem.getQuantityOnHand()).isEqualTo(70); // 100 - 30
        verify(inventoryItemRepository).save(inventoryItem);
    }

    @Test
    void adjustStock_whenResultsInNegativeStock_thenThrowsInsufficientStockException() {
        InventoryItem inventoryItem = InventoryItem.builder()
                .quantityOnHand(50)
                .quantityReserved(20)
                .build();

        when(inventoryService.getInventoryItemEntity(PRODUCT_ID)).thenReturn(inventoryItem);

        assertThatThrownBy(() -> stockMovementService.adjustStock(PRODUCT_ID, -60, "notes", USER_ID))
                .isInstanceOf(InsufficientStockException.class)
                .hasMessageContaining("Adjustment would result in negative stock");

        verify(inventoryItemRepository, never()).save(any());
    }

    @Test
    void adjustStock_whenResultsInLessThanReserved_thenThrowsInsufficientStockException() {
        InventoryItem inventoryItem = InventoryItem.builder()
                .quantityOnHand(100)
                .quantityReserved(50)
                .build();

        when(inventoryService.getInventoryItemEntity(PRODUCT_ID)).thenReturn(inventoryItem);

        assertThatThrownBy(() -> stockMovementService.adjustStock(PRODUCT_ID, -60, "notes", USER_ID))
                .isInstanceOf(InsufficientStockException.class)
                .hasMessageContaining("insufficient stock to cover reservations");

        verify(inventoryItemRepository, never()).save(any());
    }

    @Test
    void reserveStock_whenSufficientAvailableStock_thenIncreasesReserved() {
        InventoryItem inventoryItem = InventoryItem.builder()
                .quantityOnHand(100)
                .quantityReserved(20)
                .build();

        StockMovement savedMovement = StockMovement.builder()
                .id("movement-123")
                .type(MovementType.RESERVATION)
                .quantity(-30) // Stored as negative
                .build();

        StockMovementResponse expectedResponse = new StockMovementResponse();

        when(inventoryService.getInventoryItemEntity(PRODUCT_ID)).thenReturn(inventoryItem);
        when(stockMovementRepository.save(any(StockMovement.class))).thenReturn(savedMovement);
        when(stockMovementMapper.toResponse(savedMovement)).thenReturn(expectedResponse);

        stockMovementService.reserveStock(PRODUCT_ID, 30, REFERENCE_ID, USER_ID);

        assertThat(inventoryItem.getQuantityReserved()).isEqualTo(50); // 20 + 30
        assertThat(inventoryItem.getAvailableStock()).isEqualTo(50); // 100 - 50

        ArgumentCaptor<StockMovement> captor = ArgumentCaptor.forClass(StockMovement.class);
        verify(stockMovementRepository).save(captor.capture());
        assertThat(captor.getValue().getQuantity()).isEqualTo(-30); // Stored as negative
        assertThat(captor.getValue().getReferenceId()).isEqualTo(REFERENCE_ID);
    }

    @Test
    void reserveStock_whenInsufficientAvailableStock_thenThrowsInsufficientStockException() {
        InventoryItem inventoryItem = InventoryItem.builder()
                .quantityOnHand(100)
                .quantityReserved(80)
                .build();

        when(inventoryService.getInventoryItemEntity(PRODUCT_ID)).thenReturn(inventoryItem);

        assertThatThrownBy(() -> stockMovementService.reserveStock(PRODUCT_ID, 30, REFERENCE_ID, USER_ID))
                .isInstanceOf(InsufficientStockException.class)
                .hasMessageContaining("Insufficient stock to reserve");

        verify(inventoryItemRepository, never()).save(any());
    }

    @Test
    void reserveStock_whenZeroQuantity_thenThrowsInvalidQuantityException() {
        assertThatThrownBy(() -> stockMovementService.reserveStock(PRODUCT_ID, 0, REFERENCE_ID, USER_ID))
                .isInstanceOf(InvalidQuantityException.class)
                .hasMessageContaining("Reservation quantity must be greater than 0");
    }

    @Test
    void reserveStock_whenNegativeQuantity_thenThrowsInvalidQuantityException() {
        assertThatThrownBy(() -> stockMovementService.reserveStock(PRODUCT_ID, -10, REFERENCE_ID, USER_ID))
                .isInstanceOf(InvalidQuantityException.class)
                .hasMessageContaining("Reservation quantity must be greater than 0");
    }

    @Test
    void releaseReservation_whenValidQuantity_thenDecreasesReserved() {
        InventoryItem inventoryItem = InventoryItem.builder()
                .quantityOnHand(100)
                .quantityReserved(50)
                .build();

        StockMovement savedMovement = StockMovement.builder()
                .id("movement-123")
                .type(MovementType.RESERVATION_RELEASE)
                .quantity(30)
                .build();

        StockMovementResponse expectedResponse = new StockMovementResponse();

        when(inventoryService.getInventoryItemEntity(PRODUCT_ID)).thenReturn(inventoryItem);
        when(stockMovementRepository.save(any(StockMovement.class))).thenReturn(savedMovement);
        when(stockMovementMapper.toResponse(savedMovement)).thenReturn(expectedResponse);

        stockMovementService.releaseReservation(PRODUCT_ID, 30, REFERENCE_ID, USER_ID);

        assertThat(inventoryItem.getQuantityReserved()).isEqualTo(20); // 50 - 30
        assertThat(inventoryItem.getAvailableStock()).isEqualTo(80); // 100 - 20

        verify(inventoryItemRepository).save(inventoryItem);
    }

    @Test
    void releaseReservation_whenMoreThanReserved_thenThrowsInvalidQuantityException() {
        InventoryItem inventoryItem = InventoryItem.builder()
                .quantityOnHand(100)
                .quantityReserved(30)
                .build();

        when(inventoryService.getInventoryItemEntity(PRODUCT_ID)).thenReturn(inventoryItem);

        assertThatThrownBy(() -> stockMovementService.releaseReservation(PRODUCT_ID, 50, REFERENCE_ID, USER_ID))
                .isInstanceOf(InvalidQuantityException.class)
                .hasMessageContaining("Cannot release more than reserved");

        verify(inventoryItemRepository, never()).save(any());
    }

    @Test
    void releaseReservation_whenZeroQuantity_thenThrowsInvalidQuantityException() {
        assertThatThrownBy(() -> stockMovementService.releaseReservation(PRODUCT_ID, 0, REFERENCE_ID, USER_ID))
                .isInstanceOf(InvalidQuantityException.class)
                .hasMessageContaining("Release quantity must be greater than 0");
    }

    @Test
    void deductStock_whenValidQuantity_thenDecreasesOnHandAndReserved() {
        InventoryItem inventoryItem = InventoryItem.builder()
                .quantityOnHand(100)
                .quantityReserved(50)
                .build();

        StockMovement savedMovement = StockMovement.builder()
                .id("movement-123")
                .type(MovementType.DEDUCTION)
                .quantity(-30) // Stored as negative
                .build();

        StockMovementResponse expectedResponse = new StockMovementResponse();

        when(inventoryService.getInventoryItemEntity(PRODUCT_ID)).thenReturn(inventoryItem);
        when(stockMovementRepository.save(any(StockMovement.class))).thenReturn(savedMovement);
        when(stockMovementMapper.toResponse(savedMovement)).thenReturn(expectedResponse);

        stockMovementService.deductStock(PRODUCT_ID, 30, REFERENCE_ID, USER_ID);

        assertThat(inventoryItem.getQuantityOnHand()).isEqualTo(70); // 100 - 30
        assertThat(inventoryItem.getQuantityReserved()).isEqualTo(20); // 50 - 30
        assertThat(inventoryItem.getAvailableStock()).isEqualTo(50); // 70 - 20

        ArgumentCaptor<StockMovement> captor = ArgumentCaptor.forClass(StockMovement.class);
        verify(stockMovementRepository).save(captor.capture());
        assertThat(captor.getValue().getQuantity()).isEqualTo(-30); // Stored as negative
    }

    @Test
    void deductStock_whenInsufficientOnHand_thenThrowsInsufficientStockException() {
        InventoryItem inventoryItem = InventoryItem.builder()
                .quantityOnHand(20)
                .quantityReserved(50)
                .build();

        when(inventoryService.getInventoryItemEntity(PRODUCT_ID)).thenReturn(inventoryItem);

        assertThatThrownBy(() -> stockMovementService.deductStock(PRODUCT_ID, 30, REFERENCE_ID, USER_ID))
                .isInstanceOf(InsufficientStockException.class)
                .hasMessageContaining("Insufficient stock to deduct");

        verify(inventoryItemRepository, never()).save(any());
    }

    @Test
    void deductStock_whenMoreThanReserved_thenThrowsInvalidQuantityException() {
        InventoryItem inventoryItem = InventoryItem.builder()
                .quantityOnHand(100)
                .quantityReserved(20)
                .build();

        when(inventoryService.getInventoryItemEntity(PRODUCT_ID)).thenReturn(inventoryItem);

        assertThatThrownBy(() -> stockMovementService.deductStock(PRODUCT_ID, 30, REFERENCE_ID, USER_ID))
                .isInstanceOf(InvalidQuantityException.class)
                .hasMessageContaining("Cannot deduct more than reserved");

        verify(inventoryItemRepository, never()).save(any());
    }

    @Test
    void deductStock_whenZeroQuantity_thenThrowsInvalidQuantityException() {
        assertThatThrownBy(() -> stockMovementService.deductStock(PRODUCT_ID, 0, REFERENCE_ID, USER_ID))
                .isInstanceOf(InvalidQuantityException.class)
                .hasMessageContaining("Deduction quantity must be greater than 0");
    }

    @Test
    void reserveStock_whenExactlyAvailableStock_thenSucceeds() {
        InventoryItem inventoryItem = InventoryItem.builder()
                .quantityOnHand(100)
                .quantityReserved(80)
                .build();

        StockMovement savedMovement = StockMovement.builder()
                .id("movement-123")
                .build();

        when(inventoryService.getInventoryItemEntity(PRODUCT_ID)).thenReturn(inventoryItem);
        when(stockMovementRepository.save(any(StockMovement.class))).thenReturn(savedMovement);
        when(stockMovementMapper.toResponse(any())).thenReturn(new StockMovementResponse());

        stockMovementService.reserveStock(PRODUCT_ID, 20, REFERENCE_ID, USER_ID);

        assertThat(inventoryItem.getQuantityReserved()).isEqualTo(100);
        assertThat(inventoryItem.getAvailableStock()).isEqualTo(0);
    }

    @Test
    void deductStock_whenExactlyReservedAmount_thenSucceeds() {
        InventoryItem inventoryItem = InventoryItem.builder()
                .quantityOnHand(100)
                .quantityReserved(50)
                .build();

        StockMovement savedMovement = StockMovement.builder()
                .id("movement-123")
                .build();

        when(inventoryService.getInventoryItemEntity(PRODUCT_ID)).thenReturn(inventoryItem);
        when(stockMovementRepository.save(any(StockMovement.class))).thenReturn(savedMovement);
        when(stockMovementMapper.toResponse(any())).thenReturn(new StockMovementResponse());

        stockMovementService.deductStock(PRODUCT_ID, 50, REFERENCE_ID, USER_ID);

        assertThat(inventoryItem.getQuantityOnHand()).isEqualTo(50);
        assertThat(inventoryItem.getQuantityReserved()).isEqualTo(0);
    }

    @Test
    void adjustStock_whenExactlyToZero_thenSucceeds() {
        InventoryItem inventoryItem = InventoryItem.builder()
                .quantityOnHand(50)
                .quantityReserved(0)
                .build();

        StockMovement savedMovement = StockMovement.builder()
                .id("movement-123")
                .build();

        when(inventoryService.getInventoryItemEntity(PRODUCT_ID)).thenReturn(inventoryItem);
        when(stockMovementRepository.save(any(StockMovement.class))).thenReturn(savedMovement);
        when(stockMovementMapper.toResponse(any())).thenReturn(new StockMovementResponse());

        stockMovementService.adjustStock(PRODUCT_ID, -50, "Clear inventory", USER_ID);

        assertThat(inventoryItem.getQuantityOnHand()).isEqualTo(0);
    }
}
