import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { ReportService } from './report.service';
import { environment } from '../../../environments/environment';
import {
  InventorySummary,
  StockMovementReport,
  OrderSummaryReport,
  TopProductsReport,
} from '../../models';
import { SILENT_ERROR } from '../interceptors/http-context-tokens';

/**
 * ReportService unit tests.
 *
 * Covers report generation endpoints:
 * - Inventory summary reporting
 * - Stock movement reports with filtering
 * - Order summary reports with date ranges
 * - Top products analysis
 * - Silent error context handling
 */
describe('ReportService', () => {
  let service: ReportService;
  let httpMock: HttpTestingController;
  const API_URL = `${environment.apiUrl}/reports`;

  const mockInventorySummary: InventorySummary = {
    totalSkus: 150,
    totalStockValue: 125000.50,
    lowStockCount: 12,
    totalQuantityOnHand: 5000,
    totalQuantityReserved: 800,
    totalQuantityAvailable: 4200,
    generatedAt: '2026-06-02T10:00:00Z',
  };

  const mockOrderSummary: OrderSummaryReport = {
    startDate: '2026-05-01',
    endDate: '2026-05-31',
    ordersByStatus: [
      { status: 'DRAFT', count: 5, totalAmount: 1500.00 },
      { status: 'SUBMITTED', count: 10, totalAmount: 8000.00 },
      { status: 'APPROVED', count: 15, totalAmount: 12000.00 },
      { status: 'DELIVERED', count: 25, totalAmount: 30000.00 },
    ],
    totalOrders: 55,
    totalGmv: 51500.00,
    generatedAt: '2026-06-02T10:00:00Z',
  };

  const mockTopProducts: TopProductsReport = {
    days: 30,
    limit: 5,
    startDate: '2026-05-03',
    endDate: '2026-06-02',
    products: [
      {
        productId: 'prod-1',
        name: 'Premium Widget',
        sku: 'WDG-001',
        totalQuantitySold: 500,
        totalRevenue: 12500.00,
        orderCount: 25,
      },
      {
        productId: 'prod-2',
        name: 'Standard Gadget',
        sku: 'GDG-002',
        totalQuantitySold: 300,
        totalRevenue: 9000.00,
        orderCount: 20,
      },
    ],
    generatedAt: '2026-06-02T10:00:00Z',
  };

  const mockStockMovements: StockMovementReport = {
    groupBy: 'date_type',
    startDate: '2026-05-01',
    endDate: '2026-05-31',
    movements: [
      {
        date: '2026-05-15',
        type: 'RECEIPT',
        totalQuantity: 500,
        count: 5,
      },
      {
        date: '2026-05-15',
        type: 'ORDER',
        totalQuantity: -200,
        count: 12,
      },
      {
        date: '2026-05-20',
        type: 'ADJUSTMENT',
        totalQuantity: -10,
        count: 2,
      },
    ],
    generatedAt: '2026-06-02T10:00:00Z',
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        ReportService,
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    });

    service = TestBed.inject(ReportService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  describe('getInventorySummary', () => {
    it('fetches inventory summary', () => {
      service.getInventorySummary().subscribe(result => {
        expect(result).toEqual(mockInventorySummary);
        expect(result.totalSkus).toBe(150);
        expect(result.totalStockValue).toBe(125000.50);
        expect(result.lowStockCount).toBe(12);
      });

      const req = httpMock.expectOne(`${API_URL}/inventory-summary`);
      expect(req.request.method).toBe('GET');
      expect(req.request.context.get(SILENT_ERROR)).toBe(true);
      req.flush(mockInventorySummary);
    });

    it('handles empty inventory summary', () => {
      const emptyInventory: InventorySummary = {
        totalSkus: 0,
        totalStockValue: 0,
        lowStockCount: 0,
        totalQuantityOnHand: 0,
        totalQuantityReserved: 0,
        totalQuantityAvailable: 0,
        generatedAt: '2026-06-02T10:00:00Z',
      };

      service.getInventorySummary().subscribe(result => {
        expect(result.totalSkus).toBe(0);
        expect(result.totalStockValue).toBe(0);
      });

      const req = httpMock.expectOne(`${API_URL}/inventory-summary`);
      req.flush(emptyInventory);
    });

    it('handles error with silent context', () => new Promise<void>((resolve, reject) => {
      service.getInventorySummary().subscribe({
        next: () => reject(new Error('Should have failed')),
        error: (error) => {
          expect(error.status).toBe(500);
          resolve();
        },
      });

      const req = httpMock.expectOne(`${API_URL}/inventory-summary`);
      req.flush({ message: 'Database error' }, { status: 500, statusText: 'Internal Server Error' });
    }));
  });

  describe('getStockMovements', () => {
    it('fetches stock movements without parameters', () => {
      service.getStockMovements().subscribe(result => {
        expect(result).toEqual(mockStockMovements);
        expect(result.movements).toHaveLength(3);
      });

      const req = httpMock.expectOne(`${API_URL}/stock-movements`);
      expect(req.request.method).toBe('GET');
      expect(req.request.params.keys().length).toBe(0);
      req.flush(mockStockMovements);
    });

    it('fetches stock movements with date range', () => {
      service.getStockMovements({
        startDate: '2026-05-01',
        endDate: '2026-05-31',
      }).subscribe();

      const req = httpMock.expectOne(r => r.url === `${API_URL}/stock-movements`);
      expect(req.request.params.get('startDate')).toBe('2026-05-01');
      expect(req.request.params.get('endDate')).toBe('2026-05-31');
      req.flush(mockStockMovements);
    });

    it('fetches stock movements grouped by type', () => {
      service.getStockMovements({ groupBy: 'type' }).subscribe();

      const req = httpMock.expectOne(r => r.url === `${API_URL}/stock-movements`);
      expect(req.request.params.get('groupBy')).toBe('type');
      req.flush({ ...mockStockMovements, groupBy: 'type' });
    });

    it('fetches stock movements grouped by date', () => {
      service.getStockMovements({ groupBy: 'date' }).subscribe();

      const req = httpMock.expectOne(r => r.url === `${API_URL}/stock-movements`);
      expect(req.request.params.get('groupBy')).toBe('date');
      req.flush({ ...mockStockMovements, groupBy: 'date' });
    });

    it('fetches stock movements grouped by date_type', () => {
      service.getStockMovements({ groupBy: 'date_type' }).subscribe();

      const req = httpMock.expectOne(r => r.url === `${API_URL}/stock-movements`);
      expect(req.request.params.get('groupBy')).toBe('date_type');
      req.flush(mockStockMovements);
    });

    it('fetches stock movements with all parameters', () => {
      service.getStockMovements({
        startDate: '2026-05-01',
        endDate: '2026-05-31',
        groupBy: 'date_type',
      }).subscribe();

      const req = httpMock.expectOne(r => r.url === `${API_URL}/stock-movements`);
      expect(req.request.params.get('startDate')).toBe('2026-05-01');
      expect(req.request.params.get('endDate')).toBe('2026-05-31');
      expect(req.request.params.get('groupBy')).toBe('date_type');
      req.flush(mockStockMovements);
    });

    it('handles empty stock movements', () => {
      const emptyReport: StockMovementReport = {
        groupBy: 'date_type',
        startDate: '2026-05-01',
        endDate: '2026-05-31',
        movements: [],
        generatedAt: '2026-06-02T10:00:00Z',
      };

      service.getStockMovements({
        startDate: '2026-05-01',
        endDate: '2026-05-31',
      }).subscribe(result => {
        expect(result.movements).toEqual([]);
      });

      const req = httpMock.expectOne(r => r.url === `${API_URL}/stock-movements`);
      req.flush(emptyReport);
    });
  });

  describe('getOrderSummary', () => {
    it('fetches order summary without parameters', () => {
      service.getOrderSummary().subscribe(result => {
        expect(result).toEqual(mockOrderSummary);
        expect(result.ordersByStatus).toHaveLength(4);
        expect(result.totalOrders).toBe(55);
        expect(result.totalGmv).toBe(51500.00);
      });

      const req = httpMock.expectOne(`${API_URL}/order-summary`);
      expect(req.request.method).toBe('GET');
      expect(req.request.params.keys().length).toBe(0);
      req.flush(mockOrderSummary);
    });

    it('fetches order summary with date range', () => {
      service.getOrderSummary({
        startDate: '2026-05-01',
        endDate: '2026-05-31',
      }).subscribe();

      const req = httpMock.expectOne(r => r.url === `${API_URL}/order-summary`);
      expect(req.request.params.get('startDate')).toBe('2026-05-01');
      expect(req.request.params.get('endDate')).toBe('2026-05-31');
      req.flush(mockOrderSummary);
    });

    it('handles empty order summary', () => {
      const emptyReport: OrderSummaryReport = {
        startDate: '2026-05-01',
        endDate: '2026-05-31',
        ordersByStatus: [],
        totalOrders: 0,
        totalGmv: 0,
        generatedAt: '2026-06-02T10:00:00Z',
      };

      service.getOrderSummary({
        startDate: '2026-05-01',
        endDate: '2026-05-31',
      }).subscribe(result => {
        expect(result.totalOrders).toBe(0);
        expect(result.totalGmv).toBe(0);
        expect(result.ordersByStatus).toEqual([]);
      });

      const req = httpMock.expectOne(r => r.url === `${API_URL}/order-summary`);
      req.flush(emptyReport);
    });

    it('validates order summary data structure', () => {
      service.getOrderSummary().subscribe(result => {
        expect(result.startDate).toBeDefined();
        expect(result.endDate).toBeDefined();
        expect(result.generatedAt).toBeDefined();
        expect(Array.isArray(result.ordersByStatus)).toBe(true);
        expect(typeof result.totalOrders).toBe('number');
        expect(typeof result.totalGmv).toBe('number');
      });

      const req = httpMock.expectOne(`${API_URL}/order-summary`);
      req.flush(mockOrderSummary);
    });
  });

  describe('getTopProducts', () => {
    it('fetches top products without parameters', () => {
      service.getTopProducts().subscribe(result => {
        expect(result).toEqual(mockTopProducts);
        expect(result.products).toHaveLength(2);
        expect(result.products[0].name).toBe('Premium Widget');
      });

      const req = httpMock.expectOne(`${API_URL}/top-products`);
      expect(req.request.method).toBe('GET');
      expect(req.request.params.keys().length).toBe(0);
      req.flush(mockTopProducts);
    });

    it('fetches top products with days parameter', () => {
      service.getTopProducts({ days: 30 }).subscribe();

      const req = httpMock.expectOne(r => r.url === `${API_URL}/top-products`);
      expect(req.request.params.get('days')).toBe('30');
      req.flush(mockTopProducts);
    });

    it('fetches top products with 60 days', () => {
      service.getTopProducts({ days: 60 }).subscribe();

      const req = httpMock.expectOne(r => r.url === `${API_URL}/top-products`);
      expect(req.request.params.get('days')).toBe('60');
      req.flush({ ...mockTopProducts, days: 60 });
    });

    it('fetches top products with 90 days', () => {
      service.getTopProducts({ days: 90 }).subscribe();

      const req = httpMock.expectOne(r => r.url === `${API_URL}/top-products`);
      expect(req.request.params.get('days')).toBe('90');
      req.flush({ ...mockTopProducts, days: 90 });
    });

    it('fetches top products with limit parameter', () => {
      service.getTopProducts({ limit: 10 }).subscribe();

      const req = httpMock.expectOne(r => r.url === `${API_URL}/top-products`);
      expect(req.request.params.get('limit')).toBe('10');
      req.flush({ ...mockTopProducts, limit: 10 });
    });

    it('fetches top products with both days and limit', () => {
      service.getTopProducts({ days: 60, limit: 10 }).subscribe();

      const req = httpMock.expectOne(r => r.url === `${API_URL}/top-products`);
      expect(req.request.params.get('days')).toBe('60');
      expect(req.request.params.get('limit')).toBe('10');
      req.flush({ ...mockTopProducts, days: 60, limit: 10 });
    });

    it('handles empty top products result', () => {
      const emptyReport: TopProductsReport = {
        days: 30,
        limit: 5,
        startDate: '2026-05-03',
        endDate: '2026-06-02',
        products: [],
        generatedAt: '2026-06-02T10:00:00Z',
      };

      service.getTopProducts().subscribe(result => {
        expect(result.products).toEqual([]);
      });

      const req = httpMock.expectOne(`${API_URL}/top-products`);
      req.flush(emptyReport);
    });

    it('validates top products data structure', () => {
      service.getTopProducts().subscribe(result => {
        expect(typeof result.days).toBe('number');
        expect(typeof result.limit).toBe('number');
        expect(Array.isArray(result.products)).toBe(true);
        expect(result.generatedAt).toBeDefined();

        if (result.products.length > 0) {
          const product = result.products[0];
          expect(product.productId).toBeDefined();
          expect(product.name).toBeDefined();
          expect(product.sku).toBeDefined();
          expect(typeof product.totalQuantitySold).toBe('number');
          expect(typeof product.totalRevenue).toBe('number');
          expect(typeof product.orderCount).toBe('number');
        }
      });

      const req = httpMock.expectOne(`${API_URL}/top-products`);
      req.flush(mockTopProducts);
    });
  });

  describe('silent error context', () => {
    it('sets SILENT_ERROR context on all requests', () => {
      service.getInventorySummary().subscribe();
      const req1 = httpMock.expectOne(`${API_URL}/inventory-summary`);
      expect(req1.request.context.get(SILENT_ERROR)).toBe(true);
      req1.flush(mockInventorySummary);

      service.getStockMovements().subscribe();
      const req2 = httpMock.expectOne(`${API_URL}/stock-movements`);
      expect(req2.request.context.get(SILENT_ERROR)).toBe(true);
      req2.flush(mockStockMovements);

      service.getOrderSummary().subscribe();
      const req3 = httpMock.expectOne(`${API_URL}/order-summary`);
      expect(req3.request.context.get(SILENT_ERROR)).toBe(true);
      req3.flush(mockOrderSummary);

      service.getTopProducts().subscribe();
      const req4 = httpMock.expectOne(`${API_URL}/top-products`);
      expect(req4.request.context.get(SILENT_ERROR)).toBe(true);
      req4.flush(mockTopProducts);
    });
  });
});
