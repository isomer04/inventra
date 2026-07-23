import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { of, throwError } from 'rxjs';
import { MockedObject } from 'vitest';
import { OrderListComponent } from './order-list';
import { OrderService } from '../../../core/services/order.service';
import { ToastService } from '../../../core/services/toast.service';
import { Order, OrderStatus, Page } from '../../../models';

describe('OrderListComponent', () => {
  let component: OrderListComponent;
  let fixture: ComponentFixture<OrderListComponent>;
  let orderService: MockedObject<OrderService>;
  let toastService: MockedObject<ToastService>;

  const mockOrders: Order[] = [
    { id: 'o1', orderNumber: 'ORD-2026-00001', customerId: 'c1', customerName: 'Acme', status: OrderStatus.SUBMITTED, totalAmount: 110, items: [], tenantId: 't1', createdAt: '2026-01-01', updatedAt: '2026-01-01', createdBy: 'u1', createdByName: 'User One' },
    { id: 'o2', orderNumber: 'ORD-2026-00002', customerId: 'c2', customerName: 'Globex', status: OrderStatus.DRAFT, totalAmount: 220, items: [], tenantId: 't1', createdAt: '2026-01-02', updatedAt: '2026-01-02', createdBy: 'u1', createdByName: 'User One' }
  ];

  const mockPage: Page<Order> = {
    content: mockOrders,
    number: 0, size: 10, totalElements: 2, totalPages: 1
  };

  beforeEach(async () => {
    const orderSpy = { getOrders: vi.fn(), deleteOrder: vi.fn() };
    const toastSpy = { success: vi.fn(), error: vi.fn() };

    await TestBed.configureTestingModule({
      imports: [OrderListComponent, FormsModule],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: OrderService, useValue: orderSpy },
        { provide: ToastService, useValue: toastSpy }
      ]
    }).compileComponents();

    orderService = TestBed.inject(OrderService) as unknown as MockedObject<OrderService>;
    toastService = TestBed.inject(ToastService) as unknown as MockedObject<ToastService>;
    orderService.getOrders.mockReturnValue(of(mockPage));

    fixture = TestBed.createComponent(OrderListComponent);
    component = fixture.componentInstance;
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should load orders on init', () => {
    fixture.detectChanges();
    expect(orderService.getOrders).toHaveBeenCalled();
    expect(component.orders().length).toBe(2);
  });

  it('should filter by status', () => {
    fixture.detectChanges();
    component.selectedStatus = OrderStatus.SUBMITTED;
    component.onFilterChange();
    expect(orderService.getOrders).toHaveBeenCalledWith(expect.objectContaining({ status: OrderStatus.SUBMITTED }));
  });

  it('should filter by date range', () => {
    fixture.detectChanges();
    component.startDate = '2026-01-01';
    component.endDate = '2026-01-31';
    component.onFilterChange();
    expect(orderService.getOrders).toHaveBeenCalledWith(expect.objectContaining({ 
      startDate: '2026-01-01',
      endDate: '2026-01-31'
    }));
  });

  it('should navigate to page', () => {
    // The component re-syncs currentPage from the response, so the mocked page
    // must report the page that was requested.
    const multiPage: Page<Order> = { ...mockPage, totalPages: 3, number: 1 };
    orderService.getOrders.mockReturnValue(of(multiPage));
    fixture.detectChanges();
    
    component.goToPage(1);
    expect(component.currentPage()).toBe(1);
  });

  it('should delete DRAFT order', () => {
    orderService.deleteOrder.mockReturnValue(of(void 0));
    fixture.detectChanges();
    const draftOrder = mockOrders[1];
    component.pendingDeleteOrder.set(draftOrder);
    
    component.confirmDelete();
    expect(orderService.deleteOrder).toHaveBeenCalledWith('o2');
    expect(toastService.success).toHaveBeenCalled();
  });

  it('should prevent deleting non-DRAFT orders', () => {
    fixture.detectChanges();
    const submittedOrder = mockOrders[0];
    component.requestDelete(submittedOrder);
    expect(toastService.error).toHaveBeenCalledWith('Only DRAFT orders can be deleted');
    expect(component.pendingDeleteOrder()).toBeNull();
  });

  it('should show error on delete failure', () => {
    orderService.deleteOrder.mockReturnValue(throwError(() => ({ error: { message: 'Cannot delete' } })));
    fixture.detectChanges();
    component.pendingDeleteOrder.set(mockOrders[1]);
    
    component.confirmDelete();
    expect(toastService.error).toHaveBeenCalledWith('Cannot delete');
  });

  it('should cancel deletion', () => {
    fixture.detectChanges();
    component.pendingDeleteOrder.set(mockOrders[1]);
    component.cancelDelete();
    expect(component.pendingDeleteOrder()).toBeNull();
  });

  it('should clear filters', () => {
    fixture.detectChanges();
    component.selectedStatus = OrderStatus.SUBMITTED;
    component.startDate = '2026-01-01';
    component.clearFilters();
    expect(component.selectedStatus).toBe('');
    expect(component.startDate).toBe('');
  });

  it('should handle empty results', () => {
    const emptyPage: Page<Order> = { content: [], number: 0, size: 10, totalElements: 0, totalPages: 0 };
    orderService.getOrders.mockReturnValue(of(emptyPage));
    fixture.detectChanges();
    expect(component.orders().length).toBe(0);
  });

  it('should handle load error', () => {
    orderService.getOrders.mockReturnValue(throwError(() => ({ status: 500 })));
    fixture.detectChanges();
    expect(toastService.error).toHaveBeenCalledWith('Failed to load orders');
  });

  it('should sort by date descending by default', () => {
    fixture.detectChanges();
    expect(orderService.getOrders).toHaveBeenCalledWith(expect.objectContaining({ sort: 'createdAt,desc' }));
  });
});
