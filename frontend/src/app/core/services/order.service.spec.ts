import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { OrderService } from './order.service';
import { Order, OrderStatus } from '../../models';
import { environment } from '../../../environments/environment';

/**
 * OrderService unit tests.
 *
 * Covers critical order management operations:
 * - Order CRUD operations
 * - Order lifecycle state transitions
 * - Query parameter handling
 * - Order status history retrieval
 */
describe('OrderService', () => {
  let service: OrderService;
  let httpMock: HttpTestingController;
  const API_URL = `${environment.apiUrl}/orders`;

  const mockOrder: Order = {
    id: 'order-123',
    tenantId: 'tenant-1',
    orderNumber: 'ORD-2026-00001',
    customerId: 'customer-456',
    customerName: 'Acme Corp',
    status: OrderStatus.DRAFT,
    totalAmount: 275.00,
    items: [
      {
        id: 'item-1',
        orderId: 'order-123',
        productId: 'product-1',
        productName: 'Widget',
        productSku: 'WDG-001',
        quantity: 10,
        unitPrice: 25.00,
        totalPrice: 250.00,
      },
    ],
    notes: 'Test order',
    createdBy: 'user-1',
    createdByName: 'John Doe',
    createdAt: '2026-06-01T10:00:00Z',
    updatedAt: '2026-06-01T10:00:00Z',
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        OrderService,
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    });

    service = TestBed.inject(OrderService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  describe('getOrders', () => {
    it('fetches paginated orders without parameters', () => {
      const mockPage = {
        content: [mockOrder],
        number: 0, size: 20, totalElements: 1, totalPages: 1,
      };

      service.getOrders().subscribe(result => {
        expect(result).toEqual(mockPage);
        expect(result.content).toHaveLength(1);
        expect(result.content[0].orderNumber).toBe('ORD-2026-00001');
      });

      const req = httpMock.expectOne(API_URL);
      expect(req.request.method).toBe('GET');
      expect(req.request.params.keys().length).toBe(0);
      req.flush(mockPage);
    });

    it('fetches orders with pagination parameters', () => {
      service.getOrders({ page: 1, size: 10, sort: 'createdAt,desc' }).subscribe();

      const req = httpMock.expectOne(r => r.url === API_URL);
      expect(req.request.params.get('page')).toBe('1');
      expect(req.request.params.get('size')).toBe('10');
      expect(req.request.params.get('sort')).toBe('createdAt,desc');
      req.flush({ content: [], number: 1, size: 10, totalElements: 0, totalPages: 0 });
    });

    it('fetches orders filtered by status', () => {
      service.getOrders({ status: OrderStatus.SUBMITTED }).subscribe();

      const req = httpMock.expectOne(r => r.url === API_URL);
      expect(req.request.params.get('status')).toBe(OrderStatus.SUBMITTED);
      req.flush({ content: [], number: 0, size: 20, totalElements: 0, totalPages: 0 });
    });

    it('fetches orders filtered by customer', () => {
      service.getOrders({ customerId: 'customer-123' }).subscribe();

      const req = httpMock.expectOne(r => r.url === API_URL);
      expect(req.request.params.get('customerId')).toBe('customer-123');
      req.flush({ content: [], number: 0, size: 20, totalElements: 0, totalPages: 0 });
    });

    it('fetches orders filtered by date range', () => {
      service.getOrders({ startDate: '2026-06-01', endDate: '2026-06-30' }).subscribe();

      const req = httpMock.expectOne(r => r.url === API_URL);
      expect(req.request.params.get('startDate')).toBe('2026-06-01');
      expect(req.request.params.get('endDate')).toBe('2026-06-30');
      req.flush({ content: [], number: 0, size: 20, totalElements: 0, totalPages: 0 });
    });

    it('fetches orders with combined filters', () => {
      service.getOrders({
        page: 0,
        size: 50,
        status: OrderStatus.APPROVED,
        customerId: 'customer-456',
        startDate: '2026-05-01',
        endDate: '2026-05-31',
      }).subscribe();

      const req = httpMock.expectOne(r => r.url === API_URL);
      expect(req.request.params.get('page')).toBe('0');
      expect(req.request.params.get('size')).toBe('50');
      expect(req.request.params.get('status')).toBe(OrderStatus.APPROVED);
      expect(req.request.params.get('customerId')).toBe('customer-456');
      expect(req.request.params.get('startDate')).toBe('2026-05-01');
      expect(req.request.params.get('endDate')).toBe('2026-05-31');
      req.flush({ content: [], number: 0, size: 50, totalElements: 0, totalPages: 0 });
    });
  });

  describe('getOrder', () => {
    it('fetches a single order by id', () => {
      service.getOrder('order-123').subscribe(result => {
        expect(result).toEqual(mockOrder);
        expect(result.id).toBe('order-123');
        expect(result.orderNumber).toBe('ORD-2026-00001');
      });

      const req = httpMock.expectOne(`${API_URL}/order-123`);
      expect(req.request.method).toBe('GET');
      req.flush(mockOrder);
    });

    it('handles 404 when order not found', () => new Promise<void>((resolve, reject) => {
      service.getOrder('nonexistent').subscribe({
        next: () => reject(new Error('Should have failed')),
        error: (error) => {
          expect(error.status).toBe(404);
          resolve();
        },
      });

      const req = httpMock.expectOne(`${API_URL}/nonexistent`);
      req.flush({ message: 'Order not found' }, { status: 404, statusText: 'Not Found' });
    }));
  });

  describe('createOrder', () => {
    it('creates a new order', () => {
      const createRequest = {
        customerId: 'customer-456',
        items: [
          { productId: 'product-1', quantity: 10, unitPrice: 25.00 },
        ],
        notes: 'New order',
      };

      service.createOrder(createRequest).subscribe(result => {
        expect(result).toEqual(mockOrder);
        expect(result.status).toBe(OrderStatus.DRAFT);
      });

      const req = httpMock.expectOne(API_URL);
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual(createRequest);
      req.flush(mockOrder);
    });

    it('handles validation errors on create', () => new Promise<void>((resolve, reject) => {
      const invalidRequest = { customerId: '', items: [] };

      service.createOrder(invalidRequest as any).subscribe({
        next: () => reject(new Error('Should have failed')),
        error: (error) => {
          expect(error.status).toBe(400);
          resolve();
        },
      });

      const req = httpMock.expectOne(API_URL);
      req.flush(
        { message: 'Validation failed', validationErrors: { customerId: 'Required', items: 'Cannot be empty' } },
        { status: 400, statusText: 'Bad Request' }
      );
    }));
  });

  describe('updateOrder', () => {
    it('updates an existing order', () => {
      const updateRequest = {
        customerId: 'customer-789',
        items: [{ productId: 'product-2', quantity: 5, unitPrice: 30.00 }],
        notes: 'Updated order',
      };

      const updatedOrder = { ...mockOrder, customerId: 'customer-789', notes: 'Updated order' };

      service.updateOrder('order-123', updateRequest).subscribe(result => {
        expect(result.customerId).toBe('customer-789');
        expect(result.notes).toBe('Updated order');
      });

      const req = httpMock.expectOne(`${API_URL}/order-123`);
      expect(req.request.method).toBe('PUT');
      expect(req.request.body).toEqual(updateRequest);
      req.flush(updatedOrder);
    });

    it('handles 404 when updating nonexistent order', () => new Promise<void>((resolve, reject) => {
      service.updateOrder('nonexistent', {} as any).subscribe({
        next: () => reject(new Error('Should have failed')),
        error: (error) => {
          expect(error.status).toBe(404);
          resolve();
        },
      });

      const req = httpMock.expectOne(`${API_URL}/nonexistent`);
      req.flush({ message: 'Order not found' }, { status: 404, statusText: 'Not Found' });
    }));
  });

  describe('deleteOrder', () => {
    it('deletes an order', () => {
      service.deleteOrder('order-123').subscribe(result => {
        expect(result).toBeNull();
      });

      const req = httpMock.expectOne(`${API_URL}/order-123`);
      expect(req.request.method).toBe('DELETE');
      req.flush(null, { status: 204, statusText: 'No Content' });
    });

    it('handles 409 when order cannot be deleted', () => new Promise<void>((resolve, reject) => {
      service.deleteOrder('order-456').subscribe({
        next: () => reject(new Error('Should have failed')),
        error: (error) => {
          expect(error.status).toBe(409);
          resolve();
        },
      });

      const req = httpMock.expectOne(`${API_URL}/order-456`);
      req.flush(
        { message: 'Cannot delete order in SHIPPED status' },
        { status: 409, statusText: 'Conflict' }
      );
    }));
  });

  describe('submitOrder', () => {
    it('submits an order', () => {
      const submittedOrder = { ...mockOrder, status: OrderStatus.SUBMITTED };

      service.submitOrder('order-123').subscribe(result => {
        expect(result.status).toBe(OrderStatus.SUBMITTED);
      });

      const req = httpMock.expectOne(`${API_URL}/order-123/submit`);
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual({});
      req.flush(submittedOrder);
    });

    it('submits order with transition notes', () => {
      const submittedOrder = { ...mockOrder, status: OrderStatus.SUBMITTED };
      const transitionRequest = { notes: 'Submitted for approval' };

      service.submitOrder('order-123', transitionRequest).subscribe();

      const req = httpMock.expectOne(`${API_URL}/order-123/submit`);
      expect(req.request.body).toEqual(transitionRequest);
      req.flush(submittedOrder);
    });

    it('handles invalid transition error', () => new Promise<void>((resolve, reject) => {
      service.submitOrder('order-123').subscribe({
        next: () => reject(new Error('Should have failed')),
        error: (error) => {
          expect(error.status).toBe(400);
          resolve();
        },
      });

      const req = httpMock.expectOne(`${API_URL}/order-123/submit`);
      req.flush(
        { message: 'Cannot submit order in CANCELLED status' },
        { status: 400, statusText: 'Bad Request' }
      );
    }));
  });

  describe('approveOrder', () => {
    it('approves an order', () => {
      const approvedOrder = { ...mockOrder, status: OrderStatus.APPROVED };

      service.approveOrder('order-123').subscribe(result => {
        expect(result.status).toBe(OrderStatus.APPROVED);
      });

      const req = httpMock.expectOne(`${API_URL}/order-123/approve`);
      expect(req.request.method).toBe('POST');
      req.flush(approvedOrder);
    });
  });

  describe('rejectOrder', () => {
    it('rejects an order with reason', () => {
      const rejectedOrder = { ...mockOrder, status: OrderStatus.REJECTED };
      const transitionRequest = { notes: 'Insufficient budget' };

      service.rejectOrder('order-123', transitionRequest).subscribe(result => {
        expect(result.status).toBe(OrderStatus.REJECTED);
      });

      const req = httpMock.expectOne(`${API_URL}/order-123/reject`);
      expect(req.request.body).toEqual(transitionRequest);
      req.flush(rejectedOrder);
    });
  });

  describe('startPicking', () => {
    it('starts picking an order', () => {
      const pickingOrder = { ...mockOrder, status: OrderStatus.PICKING };

      service.startPicking('order-123').subscribe(result => {
        expect(result.status).toBe(OrderStatus.PICKING);
      });

      const req = httpMock.expectOne(`${API_URL}/order-123/start-picking`);
      req.flush(pickingOrder);
    });
  });

  describe('shipOrder', () => {
    it('ships an order', () => {
      const shippedOrder = { ...mockOrder, status: OrderStatus.SHIPPED };

      service.shipOrder('order-123').subscribe(result => {
        expect(result.status).toBe(OrderStatus.SHIPPED);
      });

      const req = httpMock.expectOne(`${API_URL}/order-123/ship`);
      req.flush(shippedOrder);
    });
  });

  describe('deliverOrder', () => {
    it('marks an order as delivered', () => {
      const deliveredOrder = { ...mockOrder, status: OrderStatus.DELIVERED };

      service.deliverOrder('order-123').subscribe(result => {
        expect(result.status).toBe(OrderStatus.DELIVERED);
      });

      const req = httpMock.expectOne(`${API_URL}/order-123/deliver`);
      req.flush(deliveredOrder);
    });
  });

  describe('cancelOrder', () => {
    it('cancels an order', () => {
      const cancelledOrder = { ...mockOrder, status: OrderStatus.CANCELLED };
      const transitionRequest = { notes: 'Customer requested cancellation' };

      service.cancelOrder('order-123', transitionRequest).subscribe(result => {
        expect(result.status).toBe(OrderStatus.CANCELLED);
      });

      const req = httpMock.expectOne(`${API_URL}/order-123/cancel`);
      expect(req.request.body).toEqual(transitionRequest);
      req.flush(cancelledOrder);
    });
  });

  describe('getOrderHistory', () => {
    it('fetches order status history', () => {
      const mockHistory = [
        {
          id: 'history-1',
          orderId: 'order-123',
          fromStatus: null,
          toStatus: OrderStatus.DRAFT,
          changedBy: 'user-1',
          changedByName: 'John Doe',
          changedAt: '2026-06-01T10:00:00Z',
          notes: null,
        },
        {
          id: 'history-2',
          orderId: 'order-123',
          fromStatus: OrderStatus.DRAFT,
          toStatus: OrderStatus.SUBMITTED,
          changedBy: 'user-1',
          changedByName: 'John Doe',
          changedAt: '2026-06-01T11:00:00Z',
          notes: 'Submitted for approval',
        },
      ];

      service.getOrderHistory('order-123').subscribe(result => {
        expect(result).toEqual(mockHistory);
        expect(result).toHaveLength(2);
        expect(result[1].toStatus).toBe(OrderStatus.SUBMITTED);
      });

      const req = httpMock.expectOne(`${API_URL}/order-123/history`);
      expect(req.request.method).toBe('GET');
      req.flush(mockHistory);
    });

    it('returns empty array for order without history', () => {
      service.getOrderHistory('order-new').subscribe(result => {
        expect(result).toEqual([]);
        expect(result).toHaveLength(0);
      });

      const req = httpMock.expectOne(`${API_URL}/order-new/history`);
      req.flush([]);
    });
  });
});
