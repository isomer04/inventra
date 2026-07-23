import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { of, throwError } from 'rxjs';
import { MockedObject } from 'vitest';
import { StockLevelsComponent } from './stock-levels';
import { InventoryService } from '../../../core/services/inventory.service';
import { ProductService } from '../../../core/services/product.service';
import { ToastService } from '../../../core/services/toast.service';
import { InventoryItem, Product, ProductStatus, Page } from '../../../models';

describe('StockLevelsComponent', () => {
  let component: StockLevelsComponent;
  let fixture: ComponentFixture<StockLevelsComponent>;
  let inventoryService: MockedObject<InventoryService>;
  let productService: MockedObject<ProductService>;
  let toastService: MockedObject<ToastService>;

  const mockProduct: Product = {
    id: 'p1', sku: 'SKU-001', name: 'Product 1', unitPrice: 10, status: ProductStatus.ACTIVE,
    tenantId: 't1', createdAt: '2026-01-01', updatedAt: '2026-01-01'
  };

  const mockInventory: InventoryItem[] = [
    { id: 'i1', tenantId: 't1', productId: 'p1', productName: 'Product 1', productSku: 'SKU-001', quantityOnHand: 100, quantityReserved: 10, availableQuantity: 90, reorderPoint: 20, lastUpdated: '2026-01-01' },
    { id: 'i2', tenantId: 't1', productId: 'p2', productName: 'Product 2', productSku: 'SKU-002', quantityOnHand: 5, quantityReserved: 0, availableQuantity: 5, reorderPoint: 20, lastUpdated: '2026-01-02' }
  ];

  const mockPage: Page<InventoryItem> = {
    content: mockInventory,
    number: 0, size: 10, totalElements: 2, totalPages: 1
  };

  beforeEach(async () => {
    const inventorySpy = { getInventoryItems: vi.fn(), getLowStockItems: vi.fn() };
    const productSpy = { getProduct: vi.fn() };
    const toastSpy = { success: vi.fn(), error: vi.fn() };

    await TestBed.configureTestingModule({
      imports: [StockLevelsComponent, FormsModule],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: InventoryService, useValue: inventorySpy },
        { provide: ProductService, useValue: productSpy },
        { provide: ToastService, useValue: toastSpy }
      ]
    }).compileComponents();

    inventoryService = TestBed.inject(InventoryService) as unknown as MockedObject<InventoryService>;
    productService = TestBed.inject(ProductService) as unknown as MockedObject<ProductService>;
    toastService = TestBed.inject(ToastService) as unknown as MockedObject<ToastService>;

    inventoryService.getInventoryItems.mockReturnValue(of(mockPage));
    inventoryService.getLowStockItems.mockReturnValue(of([mockInventory[1]]));
    productService.getProduct.mockReturnValue(of(mockProduct));

    fixture = TestBed.createComponent(StockLevelsComponent);
    component = fixture.componentInstance;
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should load inventory on init', () => {
    fixture.detectChanges();
    expect(inventoryService.getInventoryItems).toHaveBeenCalled();
    expect(component.inventoryItems().length).toBe(2);
  });

  it('should populate pagination signals from the page response', () => {
    fixture.detectChanges();
    expect(component.totalElements()).toBe(2);
    expect(component.totalPages()).toBe(1);
    expect(component.currentPage()).toBe(0);
  });

  it('should stop loading after inventory loads', () => {
    fixture.detectChanges();
    expect(component.loading()).toBe(false);
  });

  it('should request the default sort by product name', () => {
    fixture.detectChanges();
    expect(inventoryService.getInventoryItems).toHaveBeenCalledWith(
      expect.objectContaining({ sort: 'product.name,asc' })
    );
  });

  it('should load products for the returned inventory items', () => {
    fixture.detectChanges();
    expect(productService.getProduct).toHaveBeenCalledWith('p1');
    expect(productService.getProduct).toHaveBeenCalledWith('p2');
    expect(component.getProduct('p1')).toEqual(mockProduct);
  });

  it('should identify low stock items', () => {
    fixture.detectChanges();
    expect(component.isLowStock(mockInventory[1])).toBe(true); // available 5 <= reorder 20
    expect(component.isLowStock(mockInventory[0])).toBe(false); // available 90 > reorder 20
  });

  it('should calculate available stock from on-hand minus reserved', () => {
    fixture.detectChanges();
    expect(component.getAvailableStock(mockInventory[0])).toBe(90); // 100 - 10
    expect(component.getAvailableStock(mockInventory[1])).toBe(5); // 5 - 0
  });

  it('should classify stock levels', () => {
    fixture.detectChanges();
    expect(component.stockLevel(mockInventory[0])).toBe('ok');
    expect(component.stockLevel(mockInventory[1])).toBe('warning');
    expect(component.stockLevel({ ...mockInventory[0], quantityOnHand: 10, quantityReserved: 10 })).toBe('critical');
  });

  it('should toggle the low stock filter and load low stock items', () => {
    fixture.detectChanges();
    component.toggleLowStockFilter();
    expect(component.showLowStockOnly()).toBe(true);
    expect(inventoryService.getLowStockItems).toHaveBeenCalled();
    expect(component.inventoryItems().length).toBe(1);
    expect(component.totalPages()).toBe(1);
  });

  it('should reload full inventory when toggling the filter back off', () => {
    fixture.detectChanges();
    component.toggleLowStockFilter(); // on
    component.toggleLowStockFilter(); // off
    expect(component.showLowStockOnly()).toBe(false);
    expect(component.inventoryItems().length).toBe(2);
  });

  it('should navigate to a valid page and reload', () => {
    // The component re-syncs currentPage from the response, so the mocked page
    // must report the page that was requested.
    inventoryService.getInventoryItems.mockReturnValue(of({ ...mockPage, totalPages: 3, number: 1 }));
    fixture.detectChanges();

    const callsBefore = inventoryService.getInventoryItems.mock.calls.length;
    component.goToPage(1);
    expect(component.currentPage()).toBe(1);
    expect(inventoryService.getInventoryItems.mock.calls.length).toBe(callsBefore + 1);
  });

  it('should not navigate to an out-of-range page', () => {
    fixture.detectChanges(); // totalPages = 1
    component.goToPage(5);
    expect(component.currentPage()).toBe(0);
  });

  it('should not paginate while the low stock filter is active', () => {
    inventoryService.getInventoryItems.mockReturnValue(of({ ...mockPage, totalPages: 3 }));
    fixture.detectChanges();
    component.toggleLowStockFilter(); // showLowStockOnly = true
    component.goToPage(2);
    expect(component.currentPage()).toBe(0);
  });

  it('should open the adjustment modal for an item', () => {
    fixture.detectChanges();
    const item = mockInventory[0];
    component.openAdjustmentModal(item);
    expect(component.selectedInventory()).toBe(item);
    expect(component.showAdjustmentModal()).toBe(true);
  });

  it('should open the receipt modal for an item', () => {
    fixture.detectChanges();
    const item = mockInventory[0];
    component.openReceiptModal(item);
    expect(component.selectedInventory()).toBe(item);
    expect(component.showReceiptModal()).toBe(true);
  });

  it('should close all modals and clear the selection', () => {
    fixture.detectChanges();
    component.selectedInventory.set(mockInventory[0]);
    component.showAdjustmentModal.set(true);
    component.closeModals();
    expect(component.showAdjustmentModal()).toBe(false);
    expect(component.showReceiptModal()).toBe(false);
    expect(component.showReorderModal()).toBe(false);
    expect(component.selectedInventory()).toBeNull();
  });

  it('should reload inventory after a successful modal action', () => {
    fixture.detectChanges();
    const callsBefore = inventoryService.getInventoryItems.mock.calls.length;
    component.onModalSuccess();
    expect(component.showAdjustmentModal()).toBe(false);
    expect(inventoryService.getInventoryItems.mock.calls.length).toBe(callsBefore + 1);
  });

  it('should handle empty results', () => {
    inventoryService.getInventoryItems.mockReturnValue(of({ content: [], number: 0, size: 10, totalElements: 0, totalPages: 0 }));
    fixture.detectChanges();
    expect(component.inventoryItems().length).toBe(0);
  });

  it('should show an error toast when loading fails', () => {
    inventoryService.getInventoryItems.mockReturnValue(throwError(() => ({ status: 500 })));
    fixture.detectChanges();
    expect(toastService.error).toHaveBeenCalledWith('Failed to load inventory');
  });

  it('should compute the page number window', () => {
    inventoryService.getInventoryItems.mockReturnValue(of({ ...mockPage, totalPages: 3 }));
    fixture.detectChanges();
    expect(component.pageNumbers()).toEqual([0, 1, 2]);
  });
});
