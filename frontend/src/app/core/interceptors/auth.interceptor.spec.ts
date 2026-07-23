import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { HttpClient, HttpErrorResponse, provideHttpClient, withInterceptors } from '@angular/common/http';
import { Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { authInterceptor } from './auth.interceptor';
import { AuthService } from '../services/auth.service';
import { vi } from 'vitest';

/**
 * authInterceptor unit tests.
 *
 * Covers critical authentication interceptor paths:
 * - Token injection for protected endpoints
 * - Skipping auth for public endpoints
 * - Token refresh on 401 responses
 * - Request retry after refresh
 * - Refresh failure handling
 */
describe('authInterceptor', () => {
  let httpClient: HttpClient;
  let httpMock: HttpTestingController;
  let authService: {
    getAccessToken: ReturnType<typeof vi.fn>;
    refreshToken: ReturnType<typeof vi.fn>;
    clearSession: ReturnType<typeof vi.fn>;
  };
  let router: { navigate: ReturnType<typeof vi.fn> };

  beforeEach(() => {
    authService = {
      getAccessToken: vi.fn(),
      refreshToken: vi.fn(),
      // The interceptor calls clearSession() before redirecting on a failed refresh.
      // Omitting it here threw a TypeError inside catchError, so the redirect never ran.
      clearSession: vi.fn(),
    };
    router = {
      navigate: vi.fn(),
    };

    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: authService },
        { provide: Router, useValue: router },
      ],
    });

    httpClient = TestBed.inject(HttpClient);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('adds Authorization header with Bearer token for protected endpoints', () => {
    authService.getAccessToken.mockReturnValue('test-access-token');

    httpClient.get('/api/v1/customers').subscribe();

    const req = httpMock.expectOne('/api/v1/customers');
    expect(req.request.headers.has('Authorization')).toBe(true);
    expect(req.request.headers.get('Authorization')).toBe('Bearer test-access-token');
    req.flush([]);
  });

  it('does not add Authorization header when no token is available', () => {
    authService.getAccessToken.mockReturnValue(null);

    httpClient.get('/api/v1/products').subscribe();

    const req = httpMock.expectOne('/api/v1/products');
    expect(req.request.headers.has('Authorization')).toBe(false);
    req.flush([]);
  });

  it('skips Authorization header for login endpoint', () => {
    authService.getAccessToken.mockReturnValue('test-token');

    httpClient.post('/api/v1/auth/login', { email: 'test@example.com', password: 'password' }).subscribe();

    const req = httpMock.expectOne('/api/v1/auth/login');
    expect(req.request.headers.has('Authorization')).toBe(false);
    req.flush({ accessToken: 'new-token', refreshToken: 'refresh-token', expiresIn: 900 });
  });

  it('skips Authorization header for register endpoint', () => {
    authService.getAccessToken.mockReturnValue('test-token');

    httpClient.post('/api/v1/auth/register', { email: 'test@example.com' }).subscribe();

    const req = httpMock.expectOne('/api/v1/auth/register');
    expect(req.request.headers.has('Authorization')).toBe(false);
    req.flush({ accessToken: 'new-token', refreshToken: 'refresh-token', expiresIn: 900 });
  });

  it('skips Authorization header for refresh endpoint', () => {
    authService.getAccessToken.mockReturnValue('test-token');

    httpClient.post('/api/v1/auth/refresh', {}).subscribe();

    const req = httpMock.expectOne('/api/v1/auth/refresh');
    expect(req.request.headers.has('Authorization')).toBe(false);
    req.flush({ accessToken: 'new-token', refreshToken: 'refresh-token', expiresIn: 900 });
  });

  it('skips Authorization header for logout endpoint', () => {
    authService.getAccessToken.mockReturnValue('test-token');

    httpClient.post('/api/v1/auth/logout', {}).subscribe();

    const req = httpMock.expectOne('/api/v1/auth/logout');
    expect(req.request.headers.has('Authorization')).toBe(false);
    req.flush(null, { status: 204, statusText: 'No Content' });
  });

  it('refreshes token and retries request on 401 response', () => {
    authService.getAccessToken
      .mockReturnValueOnce('expired-token')
      .mockReturnValueOnce('new-access-token');
    authService.refreshToken.mockReturnValue(of({ accessToken: 'new-access-token', refreshToken: 'new-refresh', expiresIn: 900 }));

    httpClient.get('/api/v1/orders').subscribe({
      next: (data) => {
        expect(data).toEqual([{ id: '1', orderNumber: 'ORD-2026-00001' }]);
      },
      error: (err) => {
        throw new Error('Should not error: ' + err);
      },
    });

    const firstReq = httpMock.expectOne('/api/v1/orders');
    expect(firstReq.request.headers.get('Authorization')).toBe('Bearer expired-token');
    firstReq.flush({ message: 'Token expired' }, { status: 401, statusText: 'Unauthorized' });

    // Should trigger refresh (no HTTP expectation needed as it's mocked in service)
    
    const retryReq = httpMock.expectOne('/api/v1/orders');
    expect(retryReq.request.headers.get('Authorization')).toBe('Bearer new-access-token');
    retryReq.flush([{ id: '1', orderNumber: 'ORD-2026-00001' }]);
  });

  it('does not attempt refresh for 401 on refresh endpoint itself', () => {
    authService.getAccessToken.mockReturnValue('test-token');

    httpClient.post('/api/v1/auth/refresh', {}).subscribe({
      next: () => {
        throw new Error('Should error');
      },
      error: (error) => {
        expect(error.status).toBe(401);
        expect(authService.refreshToken).not.toHaveBeenCalled();
      },
    });

    const req = httpMock.expectOne('/api/v1/auth/refresh');
    req.flush({ message: 'Invalid refresh token' }, { status: 401, statusText: 'Unauthorized' });
  });

  it('redirects to login when token refresh fails', () => {
    authService.getAccessToken.mockReturnValue('expired-token');
    authService.refreshToken.mockReturnValue(throwError(() => ({ status: 401, message: 'Refresh failed' })));

    let caught: unknown;
    httpClient.get('/api/v1/products').subscribe({
      next: () => undefined,
      error: (error: unknown) => {
        caught = error;
      },
    });

    const req = httpMock.expectOne('/api/v1/products');
    req.flush({ message: 'Token expired' }, { status: 401, statusText: 'Unauthorized' });

    // flush() delivers synchronously, so asserting here fails the test on regression
    // instead of throwing inside the observer as an unhandled error.
    expect(caught).toBeDefined();
    expect(authService.clearSession).toHaveBeenCalled();
    expect(router.navigate).toHaveBeenCalledWith(['/login']);
  });

  it('propagates non-401 errors without refresh attempt', () => {
    authService.getAccessToken.mockReturnValue('valid-token');

    httpClient.get('/api/v1/customers/nonexistent').subscribe({
      next: () => {
        throw new Error('Should error');
      },
      error: (error) => {
        expect(error.status).toBe(404);
        expect(authService.refreshToken).not.toHaveBeenCalled();
      },
    });

    const req = httpMock.expectOne('/api/v1/customers/nonexistent');
    req.flush({ message: 'Customer not found' }, { status: 404, statusText: 'Not Found' });
  });

  it('handles empty token string as no token', () => {
    authService.getAccessToken.mockReturnValue('');

    httpClient.get('/api/v1/inventory').subscribe();

    const req = httpMock.expectOne('/api/v1/inventory');
    expect(req.request.headers.has('Authorization')).toBe(false);
    req.flush([]);
  });

  it('handles refresh returning null token', () => {
    authService.getAccessToken
      .mockReturnValueOnce('expired-token')
      .mockReturnValueOnce(null);
    authService.refreshToken.mockReturnValue(of({ accessToken: '', refreshToken: '', expiresIn: 0 }));

    let caught: HttpErrorResponse | undefined;
    httpClient.get('/api/v1/reports').subscribe({
      next: () => undefined,
      error: (error: HttpErrorResponse) => {
        caught = error;
      },
    });

    const req = httpMock.expectOne('/api/v1/reports');
    req.flush({ message: 'Token expired' }, { status: 401, statusText: 'Unauthorized' });

    // A refresh that yields no usable token clears stale session state,
    // redirects to login, and preserves the original 401 for the caller.
    expect(caught?.status).toBe(401);
    expect(authService.clearSession).toHaveBeenCalledOnce();
    expect(router.navigate).toHaveBeenCalledWith(['/login']);
  });
});
