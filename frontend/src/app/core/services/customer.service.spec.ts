import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { CustomerService } from './customer.service';
import { Customer, CustomerStatus } from '../../models';
import { environment } from '../../../environments/environment';

/**
 * CustomerService unit tests.
 *
 * Covers critical customer management operations:
 * - Customer CRUD operations
 * - Search and filtering
 * - Status filtering
 * - Pagination handling
 */
describe('CustomerService', () => {
  let service: CustomerService;
  let httpMock: HttpTestingController;
  const API_URL = `${environment.apiUrl}/customers`;

  const mockCustomer: Customer = {
    id: 'customer-123',
    tenantId: 'tenant-1',
    name: 'Acme Corporation',
    email: 'contact@acme.com',
    phone: '+1-555-0100',
    address: '123 Business Ave',
    status: CustomerStatus.ACTIVE,
    createdAt: '2026-01-15T10:00:00Z',
    updatedAt: '2026-06-01T10:00:00Z',
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        CustomerService,
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    });

    service = TestBed.inject(CustomerService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  describe('getCustomers', () => {
    it('fetches paginated customers without parameters', () => {
      const mockPage = {
        content: [mockCustomer],
        number: 0, size: 20, totalElements: 1, totalPages: 1,
      };

      service.getCustomers().subscribe(result => {
        expect(result).toEqual(mockPage);
        expect(result.content).toHaveLength(1);
        expect(result.content[0].name).toBe('Acme Corporation');
      });

      const req = httpMock.expectOne(API_URL);
      expect(req.request.method).toBe('GET');
      expect(req.request.params.keys().length).toBe(0);
      req.flush(mockPage);
    });

    it('fetches customers with pagination parameters', () => {
      service.getCustomers({ page: 1, size: 50, sort: 'name,asc' }).subscribe();

      const req = httpMock.expectOne(r => r.url === API_URL);
      expect(req.request.params.get('page')).toBe('1');
      expect(req.request.params.get('size')).toBe('50');
      expect(req.request.params.get('sort')).toBe('name,asc');
      req.flush({ content: [], number: 1, size: 50, totalElements: 0, totalPages: 0 });
    });

    it('fetches customers filtered by search term', () => {
      service.getCustomers({ search: 'Acme' }).subscribe();

      const req = httpMock.expectOne(r => r.url === API_URL);
      expect(req.request.params.get('search')).toBe('Acme');
      req.flush({ content: [mockCustomer], number: 0, size: 20, totalElements: 1, totalPages: 1 });
    });

    it('fetches customers filtered by status', () => {
      service.getCustomers({ status: CustomerStatus.ACTIVE }).subscribe();

      const req = httpMock.expectOne(r => r.url === API_URL);
      expect(req.request.params.get('status')).toBe(CustomerStatus.ACTIVE);
      req.flush({ content: [mockCustomer], number: 0, size: 20, totalElements: 1, totalPages: 1 });
    });

    it('fetches customers with combined filters', () => {
      service.getCustomers({
        page: 0,
        size: 25,
        sort: 'createdAt,desc',
        search: 'corp',
        status: CustomerStatus.ACTIVE,
      }).subscribe();

      const req = httpMock.expectOne(r => r.url === API_URL);
      expect(req.request.params.get('page')).toBe('0');
      expect(req.request.params.get('size')).toBe('25');
      expect(req.request.params.get('sort')).toBe('createdAt,desc');
      expect(req.request.params.get('search')).toBe('corp');
      expect(req.request.params.get('status')).toBe(CustomerStatus.ACTIVE);
      req.flush({ content: [], number: 0, size: 25, totalElements: 0, totalPages: 0 });
    });

    it('returns empty list when no customers match filters', () => {
      const emptyPage = {
        content: [],
        number: 0, size: 20, totalElements: 0, totalPages: 0,
      };

      service.getCustomers({ search: 'nonexistent' }).subscribe(result => {
        expect(result.content).toHaveLength(0);
        expect(result.totalElements).toBe(0);
      });

      const req = httpMock.expectOne(r => r.url === API_URL);
      req.flush(emptyPage);
    });
  });

  describe('getCustomer', () => {
    it('fetches a single customer by id', () => {
      service.getCustomer('customer-123').subscribe(result => {
        expect(result).toEqual(mockCustomer);
        expect(result.id).toBe('customer-123');
        expect(result.name).toBe('Acme Corporation');
      });

      const req = httpMock.expectOne(`${API_URL}/customer-123`);
      expect(req.request.method).toBe('GET');
      req.flush(mockCustomer);
    });

    it('handles 404 when customer not found', () => new Promise<void>((resolve, reject) => {
      service.getCustomer('nonexistent').subscribe({
        next: () => reject(new Error('Should have failed')),
        error: (error) => {
          expect(error.status).toBe(404);
          resolve();
        },
      });

      const req = httpMock.expectOne(`${API_URL}/nonexistent`);
      req.flush({ message: 'Customer not found' }, { status: 404, statusText: 'Not Found' });
    }));
  });

  describe('createCustomer', () => {
    it('creates a new customer', () => {
      const createRequest = {
        name: 'New Corp',
        email: 'contact@newcorp.com',
        phone: '+1-555-0200',
        address: '456 Innovation Blvd',
        notes: 'Priority account',
      };

      const createdCustomer: Customer = {
        ...createRequest,
        id: 'customer-456',
        tenantId: 'tenant-1',
        status: CustomerStatus.ACTIVE,
        createdAt: '2026-06-02T10:00:00Z',
        updatedAt: '2026-06-02T10:00:00Z',
      };

      service.createCustomer(createRequest).subscribe(result => {
        expect(result.id).toBe('customer-456');
        expect(result.name).toBe('New Corp');
        expect(result.status).toBe(CustomerStatus.ACTIVE);
      });

      const req = httpMock.expectOne(API_URL);
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual(createRequest);
      req.flush(createdCustomer);
    });

    it('handles validation errors on create', () => new Promise<void>((resolve, reject) => {
      const invalidRequest = {
        name: '',
        email: 'invalid-email',
        phone: '',
        address: '',
      };

      service.createCustomer(invalidRequest as any).subscribe({
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
            email: 'Invalid email format',
            phone: 'Phone is required',
          },
        },
        { status: 400, statusText: 'Bad Request' }
      );
    }));

    it('handles duplicate customer error', () => new Promise<void>((resolve, reject) => {
      const duplicateRequest = {
        name: 'Acme Corporation',
        email: 'contact@acme.com',
        phone: '+1-555-0100',
      };

      service.createCustomer(duplicateRequest as any).subscribe({
        next: () => reject(new Error('Should have failed')),
        error: (error) => {
          expect(error.status).toBe(409);
          resolve();
        },
      });

      const req = httpMock.expectOne(API_URL);
      req.flush(
        { message: 'Customer with this email already exists' },
        { status: 409, statusText: 'Conflict' }
      );
    }));
  });

  describe('updateCustomer', () => {
    it('updates an existing customer', () => {
      const updateRequest = {
        name: 'Acme Corp (Updated)',
        phone: '+1-555-0199',
      };

      const updatedCustomer: Customer = {
        ...mockCustomer,
        name: 'Acme Corp (Updated)',
        phone: '+1-555-0199',
        updatedAt: '2026-06-02T15:00:00Z',
      };

      service.updateCustomer('customer-123', updateRequest).subscribe(result => {
        expect(result.name).toBe('Acme Corp (Updated)');
        expect(result.phone).toBe('+1-555-0199');
      });

      const req = httpMock.expectOne(`${API_URL}/customer-123`);
      expect(req.request.method).toBe('PUT');
      expect(req.request.body).toEqual(updateRequest);
      req.flush(updatedCustomer);
    });

    it('handles 404 when updating nonexistent customer', () => new Promise<void>((resolve, reject) => {
      const updateRequest = { name: 'Updated Name' };

      service.updateCustomer('nonexistent', updateRequest).subscribe({
        next: () => reject(new Error('Should have failed')),
        error: (error) => {
          expect(error.status).toBe(404);
          resolve();
        },
      });

      const req = httpMock.expectOne(`${API_URL}/nonexistent`);
      req.flush({ message: 'Customer not found' }, { status: 404, statusText: 'Not Found' });
    }));

    it('handles validation errors on update', () => new Promise<void>((resolve, reject) => {
      const invalidUpdate = { email: 'not-an-email' };

      service.updateCustomer('customer-123', invalidUpdate).subscribe({
        next: () => reject(new Error('Should have failed')),
        error: (error) => {
          expect(error.status).toBe(400);
          resolve();
        },
      });

      const req = httpMock.expectOne(`${API_URL}/customer-123`);
      req.flush(
        {
          message: 'Validation failed',
          validationErrors: {
            email: 'Invalid email format',
          },
        },
        { status: 400, statusText: 'Bad Request' }
      );
    }));
  });

  describe('deleteCustomer', () => {
    it('deletes a customer', () => {
      service.deleteCustomer('customer-123').subscribe(result => {
        expect(result).toBeNull();
      });

      const req = httpMock.expectOne(`${API_URL}/customer-123`);
      expect(req.request.method).toBe('DELETE');
      req.flush(null, { status: 204, statusText: 'No Content' });
    });

    it('handles 404 when deleting nonexistent customer', () => new Promise<void>((resolve, reject) => {
      service.deleteCustomer('nonexistent').subscribe({
        next: () => reject(new Error('Should have failed')),
        error: (error) => {
          expect(error.status).toBe(404);
          resolve();
        },
      });

      const req = httpMock.expectOne(`${API_URL}/nonexistent`);
      req.flush({ message: 'Customer not found' }, { status: 404, statusText: 'Not Found' });
    }));

    it('handles 409 when customer has active orders', () => new Promise<void>((resolve, reject) => {
      service.deleteCustomer('customer-123').subscribe({
        next: () => reject(new Error('Should have failed')),
        error: (error) => {
          expect(error.status).toBe(409);
          resolve();
        },
      });

      const req = httpMock.expectOne(`${API_URL}/customer-123`);
      req.flush(
        { message: 'Cannot delete customer with active orders' },
        { status: 409, statusText: 'Conflict' }
      );
    }));
  });

  describe('business logic validation', () => {
    it('identifies inactive customers', () => {
      const inactiveCustomer: Customer = {
        ...mockCustomer,
        status: CustomerStatus.INACTIVE,
      };

      service.getCustomer('customer-456').subscribe(result => {
        expect(result.status).toBe(CustomerStatus.INACTIVE);
      });

      const req = httpMock.expectOne(`${API_URL}/customer-456`);
      req.flush(inactiveCustomer);
    });
  });

  describe('search functionality', () => {
    it('searches by customer name', () => {
      service.getCustomers({ search: 'Acme' }).subscribe();

      const req = httpMock.expectOne(r => r.url === API_URL);
      expect(req.request.params.get('search')).toBe('Acme');
      req.flush({ content: [mockCustomer], number: 0, size: 20, totalElements: 1, totalPages: 1 });
    });

    it('searches by email', () => {
      service.getCustomers({ search: 'acme.com' }).subscribe();

      const req = httpMock.expectOne(r => r.url === API_URL);
      expect(req.request.params.get('search')).toBe('acme.com');
      req.flush({ content: [mockCustomer], number: 0, size: 20, totalElements: 1, totalPages: 1 });
    });

    it('handles empty search results', () => {
      service.getCustomers({ search: 'xyz-nonexistent' }).subscribe(result => {
        expect(result.content).toHaveLength(0);
      });

      const req = httpMock.expectOne(r => r.url === API_URL);
      req.flush({ content: [], number: 0, size: 20, totalElements: 0, totalPages: 0 });
    });
  });
});
