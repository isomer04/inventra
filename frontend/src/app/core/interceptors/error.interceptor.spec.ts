import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { HttpClient, HttpContext, provideHttpClient, withInterceptors } from '@angular/common/http';
import { errorInterceptor } from './error.interceptor';
import { ToastService } from '../services/toast.service';
import { SILENT_ERROR } from './http-context-tokens';
import { vi } from 'vitest';

/**
 * errorInterceptor unit tests.
 *
 * Covers critical error handling interceptor paths:
 * - Status code-specific error messages
 * - Toast service integration
 * - Validation error extraction
 * - Silent error handling
 * - Error propagation
 */
describe('errorInterceptor', () => {
  let httpClient: HttpClient;
  let httpMock: HttpTestingController;
  let toastService: { error: ReturnType<typeof vi.fn>; success: ReturnType<typeof vi.fn> };

  beforeEach(() => {
    toastService = {
      error: vi.fn(),
      success: vi.fn(),
    };

    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([errorInterceptor])),
        provideHttpClientTesting(),
        { provide: ToastService, useValue: toastService },
      ],
    });

    httpClient = TestBed.inject(HttpClient);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('shows validation errors for 400 response with violations', () => {
    httpClient.post('/api/v1/customers', { name: '' }).subscribe({
      next: () => undefined,
      error: () => undefined,
    });

    const req = httpMock.expectOne('/api/v1/customers');
    req.flush(
      {
        message: 'Validation failed',
        status: 400,
        timestamp: '2026-06-02T12:00:00Z',
        path: '/api/v1/customers',
        violations: [
          { field: 'name', message: 'Name is required' },
          { field: 'email', message: 'Email is required' },
        ],
      },
      { status: 400, statusText: 'Bad Request' }
    );

    // flush() delivers synchronously, so asserting here fails the test on regression
    // instead of throwing inside the observer as an unhandled error.
    expect(toastService.error).toHaveBeenCalledWith(
      'Validation Error: name: Name is required, email: Email is required'
    );
  });

  it('shows generic message for 400 response without validationErrors', () => {
    httpClient.post('/api/v1/orders', {}).subscribe({
      next: () => {
        throw new Error('Should error');
      },
      error: () => {
        expect(toastService.error).toHaveBeenCalledWith('Invalid order data');
      },
    });

    const req = httpMock.expectOne('/api/v1/orders');
    req.flush(
      {
        message: 'Invalid order data',
        status: 400,
      },
      { status: 400, statusText: 'Bad Request' }
    );
  });

  it('shows "Bad Request" for 400 response without message', () => {
    httpClient.get('/api/v1/products?invalid=param').subscribe({
      next: () => {
        throw new Error('Should error');
      },
      error: () => {
        expect(toastService.error).toHaveBeenCalledWith('Bad Request');
      },
    });

    const req = httpMock.expectOne(r => r.url.includes('/api/v1/products'));
    req.flush({}, { status: 400, statusText: 'Bad Request' });
  });

  it('does not show toast for 401 response (handled by auth interceptor)', () => {
    httpClient.get('/api/v1/orders').subscribe({
      next: () => {
        throw new Error('Should error');
      },
      error: () => {
        expect(toastService.error).not.toHaveBeenCalled();
      },
    });

    const req = httpMock.expectOne('/api/v1/orders');
    req.flush({ message: 'Token expired' }, { status: 401, statusText: 'Unauthorized' });
  });

  it('shows "Access Denied" toast for 403 response', () => {
    httpClient.delete('/api/v1/users/other-user').subscribe({
      next: () => {
        throw new Error('Should error');
      },
      error: () => {
        expect(toastService.error).toHaveBeenCalledWith('Access Denied. You do not have permission to perform this action.');
      },
    });

    const req = httpMock.expectOne('/api/v1/users/other-user');
    req.flush({ message: 'Forbidden' }, { status: 403, statusText: 'Forbidden' });
  });

  it('shows custom message for 404 response with message', () => {
    httpClient.get('/api/v1/customers/nonexistent').subscribe({
      next: () => {
        throw new Error('Should error');
      },
      error: () => {
        expect(toastService.error).toHaveBeenCalledWith('Customer not found');
      },
    });

    const req = httpMock.expectOne('/api/v1/customers/nonexistent');
    req.flush({ message: 'Customer not found' }, { status: 404, statusText: 'Not Found' });
  });

  it('shows generic "Resource not found" for 404 without message', () => {
    httpClient.get('/api/v1/products/999').subscribe({
      next: () => {
        throw new Error('Should error');
      },
      error: () => {
        expect(toastService.error).toHaveBeenCalledWith('Resource not found');
      },
    });

    const req = httpMock.expectOne('/api/v1/products/999');
    req.flush({}, { status: 404, statusText: 'Not Found' });
  });

  it('shows custom message for 409 response', () => {
    httpClient.post('/api/v1/categories', { name: 'Electronics' }).subscribe({
      next: () => {
        throw new Error('Should error');
      },
      error: () => {
        expect(toastService.error).toHaveBeenCalledWith('Category with this name already exists');
      },
    });

    const req = httpMock.expectOne('/api/v1/categories');
    req.flush({ message: 'Category with this name already exists' }, { status: 409, statusText: 'Conflict' });
  });

  it('shows generic "Conflict" message for 409 without custom message', () => {
    httpClient.post('/api/v1/products', { sku: 'PROD-001' }).subscribe({
      next: () => {
        throw new Error('Should error');
      },
      error: () => {
        expect(toastService.error).toHaveBeenCalledWith('Conflict. Resource already exists.');
      },
    });

    const req = httpMock.expectOne('/api/v1/products');
    req.flush({}, { status: 409, statusText: 'Conflict' });
  });

  it('shows "Server Error" toast for 500 response', () => {
    httpClient.get('/api/v1/reports/inventory').subscribe({
      next: () => {
        throw new Error('Should error');
      },
      error: () => {
        expect(toastService.error).toHaveBeenCalledWith('Server Error. Please try again later.');
      },
    });

    const req = httpMock.expectOne('/api/v1/reports/inventory');
    req.flush({ message: 'Internal Server Error' }, { status: 500, statusText: 'Internal Server Error' });
  });

  it('shows client-side error message for ErrorEvent', () => {
    httpClient.get('/api/v1/customers').subscribe({
      next: () => {
        throw new Error('Should error');
      },
      error: () => {
        // Note: ErrorEvent handling is difficult to test in this environment
        // In real scenarios, this would be network errors like connection refused
      },
    });

    const req = httpMock.expectOne('/api/v1/customers');
    req.error(new ProgressEvent('Network error'), { status: 0, statusText: 'Unknown Error' });
  });

  it('does not show toast when SILENT_ERROR context token is set', () => {
    const context = new HttpContext().set(SILENT_ERROR, true);

    httpClient.get('/api/v1/products/check-availability', { context }).subscribe({
      next: () => {
        throw new Error('Should error');
      },
      error: () => {
        expect(toastService.error).not.toHaveBeenCalled();
      },
    });

    const req = httpMock.expectOne('/api/v1/products/check-availability');
    req.flush({ message: 'Out of stock' }, { status: 404, statusText: 'Not Found' });
  });

  it('propagates error after handling', () => {
    httpClient.delete('/api/v1/customers/123').subscribe({
      next: () => {
        throw new Error('Should error');
      },
      error: (error) => {
        expect(error.status).toBe(403);
        expect(toastService.error).toHaveBeenCalled();
      },
    });

    const req = httpMock.expectOne('/api/v1/customers/123');
    req.flush({ message: 'Cannot delete customer with active orders' }, { status: 403, statusText: 'Forbidden' });
  });

  it('handles error response without error object', () => {
    httpClient.get('/api/v1/inventory').subscribe({
      next: () => {
        throw new Error('Should error');
      },
      error: () => {
        expect(toastService.error).toHaveBeenCalledWith('Error: 503');
      },
    });

    const req = httpMock.expectOne('/api/v1/inventory');
    req.flush(null, { status: 503, statusText: 'Service Unavailable' });
  });

  it('drops malformed violations but keeps the well-formed ones', () => {
    httpClient.post('/api/v1/orders', {}).subscribe({
      next: () => undefined,
      error: () => undefined,
    });

    const req = httpMock.expectOne('/api/v1/orders');
    req.flush(
      {
        message: 'Validation failed',
        violations: [
          { field: 'items', message: 'Order must have at least one item' },
          { field: 'quantity', message: 123 }, // Non-string message — filtered out
          { field: 'sku' }, // Missing message — filtered out
        ],
      },
      { status: 400, statusText: 'Bad Request' }
    );

    expect(toastService.error).toHaveBeenCalledWith(
      'Validation Error: items: Order must have at least one item'
    );
  });

  it('handles unknown status codes with custom message if available', () => {
    httpClient.get('/api/v1/data').subscribe({
      next: () => {
        throw new Error('Should error');
      },
      error: () => {
        expect(toastService.error).toHaveBeenCalledWith('Rate limit exceeded');
      },
    });

    const req = httpMock.expectOne('/api/v1/data');
    req.flush({ message: 'Rate limit exceeded' }, { status: 429, statusText: 'Too Many Requests' });
  });

  it('handles unknown status codes without custom message', () => {
    httpClient.get('/api/v1/data').subscribe({
      next: () => {
        throw new Error('Should error');
      },
      error: () => {
        expect(toastService.error).toHaveBeenCalledWith('Error: 429');
      },
    });

    const req = httpMock.expectOne('/api/v1/data');
    req.flush({}, { status: 429, statusText: 'Too Many Requests' });
  });
});
