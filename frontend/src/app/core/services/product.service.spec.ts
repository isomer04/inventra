import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { ProductService } from './product.service';
import { Product, ProductStatus } from '../../models';
import { environment } from '../../../environments/environment';

/**
 * ProductService unit tests.
 *
 * Covers critical product management operations:
 * - Product CRUD operations
 * - Search and filtering by category, status, search term
 * - Pagination handling
 */
describe('ProductService', () => {
  let service: ProductService;
  let httpMock: HttpTestingController;
  const API_URL = `${environment.apiUrl}/products`;

  const mockProduct: Product = {
    id: 'product-123',
    tenantId: 'tenant-1',
    name: 'Premium Widget',
    description: 'High-quality widget for industrial use',
    sku: 'WDG-PREM-001',
    categoryId: 'category-456',
    categoryName: 'Electronics',
    unitPrice: 299.99,
    status: ProductStatus.ACTIVE,
    createdAt: '2026-02-01T10:00:00Z',
    updatedAt: '2026-06-01T10:00:00Z',
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        ProductService,
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    });

    service = TestBed.inject(ProductService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  describe('getProducts', () => {
    it('fetches paginated products without parameters', () => {
      const mockPage = {
        content: [mockProduct],
        number: 0, size: 20, totalElements: 1, totalPages: 1,
      };

      service.getProducts().subscribe(result => {
        expect(result).toEqual(mockPage);
        expect(result.content).toHaveLength(1);
        expect(result.content[0].sku).toBe('WDG-PREM-001');
      });

      const req = httpMock.expectOne(API_URL);
      expect(req.request.method).toBe('GET');
      expect(req.request.params.keys().length).toBe(0);
      req.flush(mockPage);
    });

    it('fetches products with pagination parameters', () => {
      service.getProducts({ page: 2, size: 50, sort: 'name,asc' }).subscribe();

      const req = httpMock.expectOne(r => r.url === API_URL);
      expect(req.request.params.get('page')).toBe('2');
      expect(req.request.params.get('size')).toBe('50');
      expect(req.request.params.get('sort')).toBe('name,asc');
      req.flush({ content: [], number: 2, size: 50, totalElements: 0, totalPages: 0 });
    });

    it('fetches products filtered by category', () => {
      service.getProducts({ categoryId: 'category-456' }).subscribe();

      const req = httpMock.expectOne(r => r.url === API_URL);
      expect(req.request.params.get('categoryId')).toBe('category-456');
      req.flush({ content: [mockProduct], number: 0, size: 20, totalElements: 1, totalPages: 1 });
    });

    it('fetches products filtered by status', () => {
      service.getProducts({ status: ProductStatus.ACTIVE }).subscribe();

      const req = httpMock.expectOne(r => r.url === API_URL);
      expect(req.request.params.get('status')).toBe(ProductStatus.ACTIVE);
      req.flush({ content: [mockProduct], number: 0, size: 20, totalElements: 1, totalPages: 1 });
    });

    it('fetches products filtered by search term', () => {
      service.getProducts({ search: 'widget' }).subscribe();

      const req = httpMock.expectOne(r => r.url === API_URL);
      expect(req.request.params.get('search')).toBe('widget');
      req.flush({ content: [mockProduct], number: 0, size: 20, totalElements: 1, totalPages: 1 });
    });

    it('fetches products with combined filters', () => {
      service.getProducts({
        page: 0,
        size: 100,
        sort: 'unitPrice,desc',
        categoryId: 'category-123',
        status: ProductStatus.ACTIVE,
        search: 'premium',
      }).subscribe();

      const req = httpMock.expectOne(r => r.url === API_URL);
      expect(req.request.params.get('page')).toBe('0');
      expect(req.request.params.get('size')).toBe('100');
      expect(req.request.params.get('sort')).toBe('unitPrice,desc');
      expect(req.request.params.get('categoryId')).toBe('category-123');
      expect(req.request.params.get('status')).toBe(ProductStatus.ACTIVE);
      expect(req.request.params.get('search')).toBe('premium');
      req.flush({ content: [], number: 0, size: 100, totalElements: 0, totalPages: 0 });
    });

    it('returns empty list when no products match filters', () => {
      const emptyPage = {
        content: [],
        number: 0, size: 20, totalElements: 0, totalPages: 0,
      };

      service.getProducts({ search: 'nonexistent-product' }).subscribe(result => {
        expect(result.content).toHaveLength(0);
        expect(result.totalElements).toBe(0);
      });

      const req = httpMock.expectOne(r => r.url === API_URL);
      req.flush(emptyPage);
    });
  });

  describe('getProduct', () => {
    it('fetches a single product by id', () => {
      service.getProduct('product-123').subscribe(result => {
        expect(result).toEqual(mockProduct);
        expect(result.id).toBe('product-123');
        expect(result.name).toBe('Premium Widget');
      });

      const req = httpMock.expectOne(`${API_URL}/product-123`);
      expect(req.request.method).toBe('GET');
      req.flush(mockProduct);
    });

    it('handles 404 when product not found', () => new Promise<void>((resolve, reject) => {
      service.getProduct('nonexistent').subscribe({
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

  describe('createProduct', () => {
    it('creates a new product', () => {
      const createRequest = {
        name: 'Standard Widget',
        description: 'Basic widget',
        sku: 'WDG-STD-001',
        categoryId: 'category-789',
        unitPrice: 99.99,
      };

      const createdProduct = {
        ...createRequest,
        id: 'product-456',
        categoryName: 'Tools',
        status: ProductStatus.ACTIVE,
        createdAt: '2026-06-02T10:00:00Z',
        updatedAt: '2026-06-02T10:00:00Z',
      };

      service.createProduct(createRequest).subscribe(result => {
        expect(result.id).toBe('product-456');
        expect(result.name).toBe('Standard Widget');
        expect(result.status).toBe(ProductStatus.ACTIVE);
      });

      const req = httpMock.expectOne(API_URL);
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual(createRequest);
      req.flush(createdProduct);
    });

    it('handles validation errors on create', () => new Promise<void>((resolve, reject) => {
      const invalidRequest = {
        name: '',
        sku: '',
        unitPrice: -10,
      };

      service.createProduct(invalidRequest as any).subscribe({
        next: () => reject(new Error('Should have failed')),
        error: (error) => {
          expect(error.status).toBe(400);
          resolve();
        },
      });

      const req = httpMock.expectOne(API_URL);
      req.flush(
        {
          message: 'Validation failed',
          validationErrors: {
            name: 'Name is required',
            sku: 'SKU is required',
            unitPrice: 'Unit price must be positive',
          },
        },
        { status: 400, statusText: 'Bad Request' }
      );
    }));

    it('handles duplicate SKU error', () => new Promise<void>((resolve, reject) => {
      const duplicateRequest = {
        name: 'Duplicate Product',
        sku: 'WDG-PREM-001', // Already exists
        categoryId: 'category-456',
        unitPrice: 199.99,
        costPrice: 100.00,
      };

      service.createProduct(duplicateRequest).subscribe({
        next: () => reject(new Error('Should have failed')),
        error: (error) => {
          expect(error.status).toBe(409);
          resolve();
        },
      });

      const req = httpMock.expectOne(API_URL);
      req.flush(
        { message: 'Product with SKU WDG-PREM-001 already exists' },
        { status: 409, statusText: 'Conflict' }
      );
    }));

    it('validates unit price is greater than cost price', () => new Promise<void>((resolve, reject) => {
      const invalidPricingRequest = {
        name: 'Bad Pricing Product',
        sku: 'BAD-001',
        categoryId: 'category-123',
        unitPrice: 50.00,
        costPrice: 75.00, // Cost > Unit Price
      };

      service.createProduct(invalidPricingRequest).subscribe({
        next: () => reject(new Error('Should have failed')),
        error: (error) => {
          expect(error.status).toBe(400);
          resolve();
        },
      });

      const req = httpMock.expectOne(API_URL);
      req.flush(
        {
          message: 'Validation failed',
          validationErrors: { unitPrice: 'Unit price must be greater than cost price' },
        },
        { status: 400, statusText: 'Bad Request' }
      );
    }));
  });

  describe('updateProduct', () => {
    it('updates an existing product', () => {
      const updateRequest = {
        name: 'Premium Widget Pro',
        unitPrice: 349.99,
        description: 'Enhanced premium widget',
      };

      const updatedProduct = {
        ...mockProduct,
        name: 'Premium Widget Pro',
        unitPrice: 349.99,
        description: 'Enhanced premium widget',
        updatedAt: '2026-06-02T15:00:00Z',
      };

      service.updateProduct('product-123', updateRequest).subscribe(result => {
        expect(result.name).toBe('Premium Widget Pro');
        expect(result.unitPrice).toBe(349.99);
      });

      const req = httpMock.expectOne(`${API_URL}/product-123`);
      expect(req.request.method).toBe('PUT');
      expect(req.request.body).toEqual(updateRequest);
      req.flush(updatedProduct);
    });

    it('updates product status', () => {
      const updateRequest = { status: ProductStatus.DISCONTINUED };

      const updatedProduct = {
        ...mockProduct,
        status: ProductStatus.DISCONTINUED,
        updatedAt: '2026-06-02T15:00:00Z',
      };

      service.updateProduct('product-123', updateRequest).subscribe(result => {
        expect(result.status).toBe(ProductStatus.DISCONTINUED);
      });

      const req = httpMock.expectOne(`${API_URL}/product-123`);
      expect(req.request.body).toEqual(updateRequest);
      req.flush(updatedProduct);
    });

    it('handles 404 when updating nonexistent product', () => new Promise<void>((resolve, reject) => {
      const updateRequest = { name: 'Updated Name' };

      service.updateProduct('nonexistent', updateRequest).subscribe({
        next: () => reject(new Error('Should have failed')),
        error: (error) => {
          expect(error.status).toBe(404);
          resolve();
        },
      });

      const req = httpMock.expectOne(`${API_URL}/nonexistent`);
      req.flush({ message: 'Product not found' }, { status: 404, statusText: 'Not Found' });
    }));

    it('handles validation errors on update', () => new Promise<void>((resolve, reject) => {
      const invalidUpdate = { unitPrice: -50, sku: '' };

      service.updateProduct('product-123', invalidUpdate).subscribe({
        next: () => reject(new Error('Should have failed')),
        error: (error) => {
          expect(error.status).toBe(400);
          resolve();
        },
      });

      const req = httpMock.expectOne(`${API_URL}/product-123`);
      req.flush(
        {
          message: 'Validation failed',
          validationErrors: {
            unitPrice: 'Unit price must be positive',
            sku: 'SKU cannot be empty',
          },
        },
        { status: 400, statusText: 'Bad Request' }
      );
    }));
  });

  describe('deleteProduct', () => {
    it('deletes a product', () => {
      service.deleteProduct('product-123').subscribe(result => {
        expect(result).toBeNull();
      });

      const req = httpMock.expectOne(`${API_URL}/product-123`);
      expect(req.request.method).toBe('DELETE');
      req.flush(null, { status: 204, statusText: 'No Content' });
    });

    it('handles 404 when deleting nonexistent product', () => new Promise<void>((resolve, reject) => {
      service.deleteProduct('nonexistent').subscribe({
        next: () => reject(new Error('Should have failed')),
        error: (error) => {
          expect(error.status).toBe(404);
          resolve();
        },
      });

      const req = httpMock.expectOne(`${API_URL}/nonexistent`);
      req.flush({ message: 'Product not found' }, { status: 404, statusText: 'Not Found' });
    }));

    it('handles 409 when product has inventory', () => new Promise<void>((resolve, reject) => {
      service.deleteProduct('product-123').subscribe({
        next: () => reject(new Error('Should have failed')),
        error: (error) => {
          expect(error.status).toBe(409);
          resolve();
        },
      });

      const req = httpMock.expectOne(`${API_URL}/product-123`);
      req.flush(
        { message: 'Cannot delete product with existing inventory' },
        { status: 409, statusText: 'Conflict' }
      );
    }));

    it('handles 409 when product is used in orders', () => new Promise<void>((resolve, reject) => {
      service.deleteProduct('product-456').subscribe({
        next: () => reject(new Error('Should have failed')),
        error: (error) => {
          expect(error.status).toBe(409);
          resolve();
        },
      });

      const req = httpMock.expectOne(`${API_URL}/product-456`);
      req.flush(
        { message: 'Cannot delete product referenced in orders' },
        { status: 409, statusText: 'Conflict' }
      );
    }));
  });

  describe('business logic validation', () => {
    // NOTE: the former 'calculates profit margin' test was dropped — `Product`
    // carries no `costPrice`, so there is no cost data in the domain model to
    // compute a margin from.

    it('exposes the unit price returned by the API', () => {
      const product: Product = {
        ...mockProduct,
        unitPrice: 299.99,
      };

      service.getProduct('product-123').subscribe(result => {
        expect(result.unitPrice).toBeCloseTo(299.99, 2);
      });

      const req = httpMock.expectOne(`${API_URL}/product-123`);
      req.flush(product);
    });

    it('identifies discontinued products', () => {
      const discontinuedProduct: Product = {
        ...mockProduct,
        status: ProductStatus.DISCONTINUED,
      };

      service.getProduct('product-789').subscribe(result => {
        expect(result.status).toBe(ProductStatus.DISCONTINUED);
      });

      const req = httpMock.expectOne(`${API_URL}/product-789`);
      req.flush(discontinuedProduct);
    });
  });

  describe('search and filter functionality', () => {
    it('searches by product name', () => {
      service.getProducts({ search: 'Widget' }).subscribe();

      const req = httpMock.expectOne(r => r.url === API_URL);
      expect(req.request.params.get('search')).toBe('Widget');
      req.flush({ content: [mockProduct], number: 0, size: 20, totalElements: 1, totalPages: 1 });
    });

    it('searches by SKU', () => {
      service.getProducts({ search: 'WDG-PREM-001' }).subscribe();

      const req = httpMock.expectOne(r => r.url === API_URL);
      expect(req.request.params.get('search')).toBe('WDG-PREM-001');
      req.flush({ content: [mockProduct], number: 0, size: 20, totalElements: 1, totalPages: 1 });
    });

    it('filters by active status only', () => {
      service.getProducts({ status: ProductStatus.ACTIVE }).subscribe();

      const req = httpMock.expectOne(r => r.url === API_URL);
      expect(req.request.params.get('status')).toBe(ProductStatus.ACTIVE);
      req.flush({ content: [mockProduct], number: 0, size: 20, totalElements: 1, totalPages: 1 });
    });

    it('filters by category', () => {
      service.getProducts({ categoryId: 'category-electronics' }).subscribe();

      const req = httpMock.expectOne(r => r.url === API_URL);
      expect(req.request.params.get('categoryId')).toBe('category-electronics');
      req.flush({ content: [], number: 0, size: 20, totalElements: 0, totalPages: 0 });
    });
  });
});
