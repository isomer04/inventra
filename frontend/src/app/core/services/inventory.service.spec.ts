import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { InventoryService } from './inventory.service';
import { InventoryItem } from '../../models';
import { environment } from '../../../environments/environment';

/**
 * InventoryService unit tests.
 *
 * Covers critical inventory management operations:
 * - Inventory item queries
 * - Low stock alerts
 * - Stock receipts and adjustments
 * - Reorder point management
 */
describe('InventoryService', () => {
  let service: InventoryService;
  let httpMock: HttpTestingController;
  const API_URL = `${environment.apiUrl}/inventory`;

  const mockInventoryItem: InventoryItem = {
    id: 'inventory-123',
    tenantId: 'tenant-1',
    productId: 'product-123',
    productName: 'Widget Pro',
    productSku: 'WDG-PRO-001',
    quantityOnHand: 100,
    quantityReserved: 20,
    availableQuantity: 80,
    reorderPoint: 25,
    lastUpdated: '2026-06-01T10:00:00Z',
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        InventoryService,
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    });

    service = TestBed.inject(InventoryService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  describe('getInventoryItems', () => {
    it('fetches paginated inventory items without parameters', () => {
      const mockPage = {
        content: [mockInventoryItem],
        totalElements: 1,
        totalPages: 1,
        size: 20,
        number: 0,
      };

      service.getInventoryItems().subscribe(result => {
        expect(result).toEqual(mockPage);
        expect(result.content).toHaveLength(1);
        expect(result.content[0].productSku).toBe('WDG-PRO-001');
      });

      const req = httpMock.expectOne(API_URL);
      expect(req.request.method).toBe('GET');
      expect(req.request.params.keys().length).toBe(0);
      req.flush(mockPage);
    });

    it('fetches inventory items with pagination parameters', () => {
      service.getInventoryItems({ page: 2, size: 50, sort: 'productName,asc' }).subscribe();

      const req = httpMock.expectOne(r => r.url === API_URL);
      expect(req.request.params.get('page')).toBe('2');
      expect(req.request.params.get('size')).toBe('50');
      expect(req.request.params.get('sort')).toBe('productName,asc');
      req.flush({ content: [], totalElements: 0, totalPages: 0, size: 50, number: 2 });
    });

    it('returns empty list when no inventory items exist', () => {
      const emptyPage = {
        content: [],
        totalElements: 0,
        totalPages: 0,
        size: 20,
        number: 0,
      };

      service.getInventoryItems().subscribe(result => {
        expect(result.content).toHaveLength(0);
        expect(result.totalElements).toBe(0);
      });

      const req = httpMock.expectOne(API_URL);
      req.flush(emptyPage);
    });
  });

  describe('getInventoryByProduct', () => {
    it('fetches inventory for a specific product', () => {
      service.getInventoryByProduct('product-123').subscribe(result => {
        expect(result).toEqual(mockInventoryItem);
        expect(result.productId).toBe('product-123');
        expect(result.availableQuantity).toBe(80);
      });

      const req = httpMock.expectOne(`${API_URL}/product-123`);
      expect(req.request.method).toBe('GET');
      req.flush(mockInventoryItem);
    });

    it('handles 404 when product inventory not found', () => new Promise<void>((resolve, reject) => {
      service.getInventoryByProduct('nonexistent').subscribe({
        next: () => reject(new Error('Should have failed')),
        error: (error) => {
          expect(error.status).toBe(404);
          resolve();
        },
      });

      const req = httpMock.expectOne(`${API_URL}/nonexistent`);
      req.flush({ message: 'Inventory not found for product' }, { status: 404, statusText: 'Not Found' });
    }));
  });

  describe('getLowStockItems', () => {
    it('fetches items below reorder point', () => {
      const lowStockItems = [
        { ...mockInventoryItem, quantityOnHand: 20, availableQuantity: 15, reorderPoint: 25 },
        { ...mockInventoryItem, productId: 'product-456', quantityOnHand: 5, availableQuantity: 5, reorderPoint: 10 },
      ];

      service.getLowStockItems().subscribe(result => {
        expect(result).toEqual(lowStockItems);
        expect(result).toHaveLength(2);
        expect(result[0].quantityOnHand).toBeLessThan(result[0].reorderPoint);
        expect(result[1].quantityOnHand).toBeLessThan(result[1].reorderPoint);
      });

      const req = httpMock.expectOne(`${API_URL}/low-stock`);
      expect(req.request.method).toBe('GET');
      req.flush(lowStockItems);
    });

    it('returns empty array when no low stock items', () => {
      service.getLowStockItems().subscribe(result => {
        expect(result).toEqual([]);
        expect(result).toHaveLength(0);
      });

      const req = httpMock.expectOne(`${API_URL}/low-stock`);
      req.flush([]);
    });
  });

  describe('receiveStock', () => {
    it('receives stock for a product', () => {
      const receiptRequest = {
        quantity: 50,
        notes: 'Received from supplier XYZ',
      };

      const updatedInventory = {
        ...mockInventoryItem,
        quantityOnHand: 150,
        availableQuantity: 130,
        lastUpdated: '2026-06-02T14:00:00Z',
      };

      service.receiveStock('product-123', receiptRequest).subscribe(result => {
        expect(result.quantityOnHand).toBe(150);
        expect(result.availableQuantity).toBe(130);
      });

      const req = httpMock.expectOne(`${API_URL}/product-123/receive`);
      expect(req.request.method).toBe('PUT');
      expect(req.request.body).toEqual(receiptRequest);
      req.flush(updatedInventory);
    });

    it('handles validation error for negative quantity', () => new Promise<void>((resolve, reject) => {
      const invalidRequest = { quantity: -10, notes: 'Invalid' };

      service.receiveStock('product-123', invalidRequest).subscribe({
        next: () => reject(new Error('Should have failed')),
        error: (error) => {
          expect(error.status).toBe(400);
          resolve();
        },
      });

      const req = httpMock.expectOne(`${API_URL}/product-123/receive`);
      req.flush(
        { message: 'Validation failed', validationErrors: { quantity: 'Must be positive' } },
        { status: 400, statusText: 'Bad Request' }
      );
    }));

    it('handles 404 when product does not exist', () => new Promise<void>((resolve, reject) => {
      const receiptRequest = { quantity: 50, notes: 'Test' };

      service.receiveStock('nonexistent', receiptRequest).subscribe({
        next: () => reject(new Error('Should have failed')),
        error: (error) => {
          expect(error.status).toBe(404);
          resolve();
        },
      });

      const req = httpMock.expectOne(`${API_URL}/nonexistent/receive`);
      req.flush({ message: 'Product not found' }, { status: 404, statusText: 'Not Found' });
    }));
  });

  describe('adjustStock', () => {
    it('adjusts stock with positive adjustment', () => {
      const adjustmentRequest = {
        quantity: 10,
        notes: 'Found additional units during inventory count',
      };

      const updatedInventory = {
        ...mockInventoryItem,
        quantityOnHand: 110,
        availableQuantity: 90,
      };

      service.adjustStock('product-123', adjustmentRequest).subscribe(result => {
        expect(result.quantityOnHand).toBe(110);
        expect(result.availableQuantity).toBe(90);
      });

      const req = httpMock.expectOne(`${API_URL}/product-123/adjust`);
      expect(req.request.method).toBe('PUT');
      expect(req.request.body).toEqual(adjustmentRequest);
      req.flush(updatedInventory);
    });

    it('adjusts stock with negative adjustment (shrinkage)', () => {
      const adjustmentRequest = {
        quantity: -15,
        notes: 'Damaged units',
      };

      const updatedInventory = {
        ...mockInventoryItem,
        quantityOnHand: 85,
        availableQuantity: 65,
      };

      service.adjustStock('product-123', adjustmentRequest).subscribe(result => {
        expect(result.quantityOnHand).toBe(85);
      });

      const req = httpMock.expectOne(`${API_URL}/product-123/adjust`);
      expect(req.request.body).toEqual(adjustmentRequest);
      req.flush(updatedInventory);
    });

    it('handles insufficient stock for negative adjustment', () => new Promise<void>((resolve, reject) => {
      const adjustmentRequest = {
        quantity: -200,
        notes: 'Test',
      };

      service.adjustStock('product-123', adjustmentRequest).subscribe({
        next: () => reject(new Error('Should have failed')),
        error: (error) => {
          expect(error.status).toBe(400);
          resolve();
        },
      });

      const req = httpMock.expectOne(`${API_URL}/product-123/adjust`);
      req.flush(
        { message: 'Insufficient stock. Cannot adjust by -200 when only 100 available.' },
        { status: 400, statusText: 'Bad Request' }
      );
    }));

    it('requires notes for adjustments', () => new Promise<void>((resolve, reject) => {
      const adjustmentRequest = {
        quantity: 10,
        notes: '',
      };

      service.adjustStock('product-123', adjustmentRequest).subscribe({
        next: () => reject(new Error('Should have failed')),
        error: (error) => {
          expect(error.status).toBe(400);
          resolve();
        },
      });

      const req = httpMock.expectOne(`${API_URL}/product-123/adjust`);
      req.flush(
        { message: 'Validation failed', validationErrors: { notes: 'Notes are required' } },
        { status: 400, statusText: 'Bad Request' }
      );
    }));
  });

  describe('updateReorderPoint', () => {
    it('updates reorder point for a product', () => {
      const reorderRequest = { reorderPoint: 50 };

      const updatedInventory = {
        ...mockInventoryItem,
        reorderPoint: 50,
      };

      service.updateReorderPoint('product-123', reorderRequest).subscribe(result => {
        expect(result.reorderPoint).toBe(50);
      });

      const req = httpMock.expectOne(`${API_URL}/product-123/reorder-point`);
      expect(req.request.method).toBe('PUT');
      expect(req.request.body).toEqual(reorderRequest);
      req.flush(updatedInventory);
    });

    it('handles validation error for negative reorder point', () => new Promise<void>((resolve, reject) => {
      const invalidRequest = { reorderPoint: -5 };

      service.updateReorderPoint('product-123', invalidRequest).subscribe({
        next: () => reject(new Error('Should have failed')),
        error: (error) => {
          expect(error.status).toBe(400);
          resolve();
        },
      });

      const req = httpMock.expectOne(`${API_URL}/product-123/reorder-point`);
      req.flush(
        { message: 'Validation failed', validationErrors: { reorderPoint: 'Must be non-negative' } },
        { status: 400, statusText: 'Bad Request' }
      );
    }));

    it('allows setting reorder point to zero', () => {
      const reorderRequest = { reorderPoint: 0 };

      const updatedInventory = {
        ...mockInventoryItem,
        reorderPoint: 0,
      };

      service.updateReorderPoint('product-123', reorderRequest).subscribe(result => {
        expect(result.reorderPoint).toBe(0);
      });

      const req = httpMock.expectOne(`${API_URL}/product-123/reorder-point`);
      expect(req.request.body).toEqual(reorderRequest);
      req.flush(updatedInventory);
    });

    it('handles 404 when product not found', () => new Promise<void>((resolve, reject) => {
      const reorderRequest = { reorderPoint: 30 };

      service.updateReorderPoint('nonexistent', reorderRequest).subscribe({
        next: () => reject(new Error('Should have failed')),
        error: (error) => {
          expect(error.status).toBe(404);
          resolve();
        },
      });

      const req = httpMock.expectOne(`${API_URL}/nonexistent/reorder-point`);
      req.flush({ message: 'Product not found' }, { status: 404, statusText: 'Not Found' });
    }));
  });

  describe('business logic validation', () => {
    it('calculates availableQuantity as (quantityOnHand - quantityReserved)', () => {
      const item: InventoryItem = {
        ...mockInventoryItem,
        quantityOnHand: 150,
        quantityReserved: 40,
        availableQuantity: 110,
      };

      service.getInventoryByProduct('product-123').subscribe(result => {
        expect(result.availableQuantity).toBe(result.quantityOnHand - result.quantityReserved);
      });

      const req = httpMock.expectOne(`${API_URL}/product-123`);
      req.flush(item);
    });

    it('identifies low stock when quantityOnHand < reorderPoint', () => {
      const lowStockItem: InventoryItem = {
        ...mockInventoryItem,
        quantityOnHand: 20,
        reorderPoint: 25,
      };

      service.getInventoryByProduct('product-123').subscribe(result => {
        expect(result.quantityOnHand).toBeLessThan(result.reorderPoint);
      });

      const req = httpMock.expectOne(`${API_URL}/product-123`);
      req.flush(lowStockItem);
    });
  });
});
