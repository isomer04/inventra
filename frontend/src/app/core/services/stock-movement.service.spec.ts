import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { StockMovementService } from './stock-movement.service';
import { StockMovement, MovementType } from '../../models';
import { environment } from '../../../environments/environment';

/**
 * StockMovementService unit tests.
 *
 * Covers stock movement tracking:
 * - Fetching movement history
 * - Filtering by product, type, date range
 * - Pagination handling
 */
describe('StockMovementService', () => {
  let service: StockMovementService;
  let httpMock: HttpTestingController;
  const API_URL = `${environment.apiUrl}/inventory/movements`;

  const mockMovement: StockMovement = {
    id: 'movement-123',
    tenantId: 'tenant-1',
    productId: 'product-456',
    productName: 'Widget',
    productSku: 'WDG-001',
    type: MovementType.RECEIPT,
    quantity: 50,
    notes: 'Received from supplier',
    createdBy: 'user-789',
    createdByName: 'John Doe',
    createdAt: '2026-06-01T10:00:00Z',
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        StockMovementService,
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    });

    service = TestBed.inject(StockMovementService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  describe('getMovements', () => {
    it('fetches paginated movements without parameters', () => {
      const mockPage = {
        content: [mockMovement],
        number: 0, size: 20, totalElements: 1, totalPages: 1,
      };

      service.getMovements().subscribe(result => {
        expect(result).toEqual(mockPage);
        expect(result.content).toHaveLength(1);
        expect(result.content[0].type).toBe(MovementType.RECEIPT);
      });

      const req = httpMock.expectOne(API_URL);
      expect(req.request.method).toBe('GET');
      expect(req.request.params.keys().length).toBe(0);
      req.flush(mockPage);
    });

    it('fetches movements with pagination parameters', () => {
      service.getMovements({ page: 1, size: 50, sort: 'createdAt,desc' }).subscribe();

      const req = httpMock.expectOne(r => r.url === API_URL);
      expect(req.request.params.get('page')).toBe('1');
      expect(req.request.params.get('size')).toBe('50');
      expect(req.request.params.get('sort')).toBe('createdAt,desc');
      req.flush({ content: [], number: 1, size: 50, totalElements: 0, totalPages: 0 });
    });

    it('fetches movements filtered by product', () => {
      service.getMovements({ productId: 'product-456' }).subscribe();

      const req = httpMock.expectOne(r => r.url === API_URL);
      expect(req.request.params.get('productId')).toBe('product-456');
      req.flush({ content: [mockMovement], number: 0, size: 20, totalElements: 1, totalPages: 1 });
    });

    it('fetches movements filtered by type', () => {
      service.getMovements({ type: MovementType.ADJUSTMENT }).subscribe();

      const req = httpMock.expectOne(r => r.url === API_URL);
      expect(req.request.params.get('type')).toBe(MovementType.ADJUSTMENT);
      req.flush({ content: [], number: 0, size: 20, totalElements: 0, totalPages: 0 });
    });

    it('fetches movements filtered by date range', () => {
      service.getMovements({ startDate: '2026-06-01', endDate: '2026-06-30' }).subscribe();

      const req = httpMock.expectOne(r => r.url === API_URL);
      expect(req.request.params.get('startDate')).toBe('2026-06-01');
      expect(req.request.params.get('endDate')).toBe('2026-06-30');
      req.flush({ content: [], number: 0, size: 20, totalElements: 0, totalPages: 0 });
    });

    it('fetches movements with combined filters', () => {
      service.getMovements({
        page: 0,
        size: 100,
        sort: 'createdAt,asc',
        productId: 'product-123',
        type: MovementType.DEDUCTION,
        startDate: '2026-05-01',
        endDate: '2026-05-31',
      }).subscribe();

      const req = httpMock.expectOne(r => r.url === API_URL);
      expect(req.request.params.get('page')).toBe('0');
      expect(req.request.params.get('size')).toBe('100');
      expect(req.request.params.get('sort')).toBe('createdAt,asc');
      expect(req.request.params.get('productId')).toBe('product-123');
      expect(req.request.params.get('type')).toBe(MovementType.DEDUCTION);
      expect(req.request.params.get('startDate')).toBe('2026-05-01');
      expect(req.request.params.get('endDate')).toBe('2026-05-31');
      req.flush({ content: [], number: 0, size: 100, totalElements: 0, totalPages: 0 });
    });

    it('returns empty list when no movements match filters', () => {
      const emptyPage = {
        content: [],
        number: 0, size: 20, totalElements: 0, totalPages: 0,
      };

      service.getMovements({ productId: 'nonexistent' }).subscribe(result => {
        expect(result.content).toHaveLength(0);
        expect(result.totalElements).toBe(0);
      });

      const req = httpMock.expectOne(r => r.url === API_URL);
      req.flush(emptyPage);
    });
  });

  describe('getMovementsByProduct', () => {
    it('fetches all movements for a specific product', () => {
      const movements = [mockMovement, { ...mockMovement, id: 'movement-456', quantity: -10 }];

      service.getMovementsByProduct('product-456').subscribe(result => {
        expect(result).toEqual(movements);
        expect(result).toHaveLength(2);
        expect(result.every(m => m.productId === 'product-456')).toBe(true);
      });

      const req = httpMock.expectOne(`${API_URL}/product-456`);
      expect(req.request.method).toBe('GET');
      req.flush(movements);
    });

    it('returns empty array for product with no movements', () => {
      service.getMovementsByProduct('product-new').subscribe(result => {
        expect(result).toEqual([]);
        expect(result).toHaveLength(0);
      });

      const req = httpMock.expectOne(`${API_URL}/product-new`);
      req.flush([]);
    });

    it('handles 404 when product not found', () => new Promise<void>((resolve, reject) => {
      service.getMovementsByProduct('nonexistent').subscribe({
        next: () => reject(new Error('Should have failed')),
        error: (error) => {
          expect(error.status).toBe(404);
          resolve();
        },
      });

      const req = httpMock.expectOne(`${API_URL}/nonexistent`);
      req.flush({ message: 'Product not found' }, { status: 404, statusText: 'Not Found' });
    }));
  });

  describe('movement types', () => {
    it('fetches RECEIPT movements', () => {
      const receipt = { ...mockMovement, type: MovementType.RECEIPT, quantity: 50 };

      service.getMovements({ type: MovementType.RECEIPT }).subscribe();

      const req = httpMock.expectOne(r => r.url === API_URL);
      expect(req.request.params.get('type')).toBe(MovementType.RECEIPT);
      req.flush({ content: [receipt], number: 0, size: 20, totalElements: 1, totalPages: 1 });
    });

    it('fetches ADJUSTMENT movements', () => {
      service.getMovements({ type: MovementType.ADJUSTMENT }).subscribe();

      const req = httpMock.expectOne(r => r.url === API_URL);
      expect(req.request.params.get('type')).toBe(MovementType.ADJUSTMENT);
      req.flush({ content: [], number: 0, size: 20, totalElements: 0, totalPages: 0 });
    });

    it('fetches ORDER_SHIPMENT movements', () => {
      const shipment = { ...mockMovement, type: MovementType.DEDUCTION, quantity: -25 };

      service.getMovements({ type: MovementType.DEDUCTION }).subscribe();

      const req = httpMock.expectOne(r => r.url === API_URL);
      expect(req.request.params.get('type')).toBe(MovementType.DEDUCTION);
      req.flush({ content: [shipment], number: 0, size: 20, totalElements: 1, totalPages: 1 });
    });

    it('fetches SHRINKAGE movements', () => {
      const shrinkage = { ...mockMovement, type: MovementType.ADJUSTMENT, quantity: -5 };

      service.getMovements({ type: MovementType.ADJUSTMENT }).subscribe();

      const req = httpMock.expectOne(r => r.url === API_URL);
      expect(req.request.params.get('type')).toBe(MovementType.ADJUSTMENT);
      req.flush({ content: [shrinkage], number: 0, size: 20, totalElements: 1, totalPages: 1 });
    });
  });

  describe('quantity changes', () => {
    it('tracks positive quantity changes (receipts)', () => {
      const receipt: StockMovement = {
        ...mockMovement,
        quantity: 100,
      };

      service.getMovementsByProduct('product-123').subscribe(result => {
        expect(result[0].quantity).toBeGreaterThan(0);
        expect(result[0].type).toBe(MovementType.RECEIPT);
      });

      const req = httpMock.expectOne(`${API_URL}/product-123`);
      req.flush([receipt]);
    });

    it('tracks negative quantity changes (deductions)', () => {
      const deduction: StockMovement = {
        ...mockMovement,
        type: MovementType.DEDUCTION,
        quantity: -30,
      };

      service.getMovementsByProduct('product-123').subscribe(result => {
        expect(result[0].quantity).toBeLessThan(0);
        expect(result[0].type).toBe(MovementType.DEDUCTION);
      });

      const req = httpMock.expectOne(`${API_URL}/product-123`);
      req.flush([deduction]);
    });

    it('includes movement notes for audit trail', () => {
      const movement = { ...mockMovement, notes: 'Annual inventory count adjustment' };

      service.getMovementsByProduct('product-123').subscribe(result => {
        expect(result[0].notes).toBeTruthy();
        expect(result[0].notes).toBe('Annual inventory count adjustment');
      });

      const req = httpMock.expectOne(`${API_URL}/product-123`);
      req.flush([movement]);
    });

    it('includes user who created the movement', () => {
      const movement = {
        ...mockMovement,
        createdBy: 'user-456',
        createdByName: 'Jane Smith',
      };

      service.getMovementsByProduct('product-123').subscribe(result => {
        expect(result[0].createdBy).toBe('user-456');
        expect(result[0].createdByName).toBe('Jane Smith');
      });

      const req = httpMock.expectOne(`${API_URL}/product-123`);
      req.flush([movement]);
    });
  });

  describe('date filtering', () => {
    it('filters movements by start date', () => {
      service.getMovements({ startDate: '2026-06-01' }).subscribe();

      const req = httpMock.expectOne(r => r.url === API_URL);
      expect(req.request.params.get('startDate')).toBe('2026-06-01');
      expect(req.request.params.has('endDate')).toBe(false);
      req.flush({ content: [], number: 0, size: 20, totalElements: 0, totalPages: 0 });
    });

    it('filters movements by end date', () => {
      service.getMovements({ endDate: '2026-06-30' }).subscribe();

      const req = httpMock.expectOne(r => r.url === API_URL);
      expect(req.request.params.has('startDate')).toBe(false);
      expect(req.request.params.get('endDate')).toBe('2026-06-30');
      req.flush({ content: [], number: 0, size: 20, totalElements: 0, totalPages: 0 });
    });

    it('filters movements by date range', () => {
      service.getMovements({ startDate: '2026-06-01', endDate: '2026-06-30' }).subscribe();

      const req = httpMock.expectOne(r => r.url === API_URL);
      expect(req.request.params.get('startDate')).toBe('2026-06-01');
      expect(req.request.params.get('endDate')).toBe('2026-06-30');
      req.flush({ content: [], number: 0, size: 20, totalElements: 0, totalPages: 0 });
    });
  });
});
