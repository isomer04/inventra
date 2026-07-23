import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { AuthService } from './auth.service';
import { UserRole, UserStatus } from '../../models';

/**
 * AuthService unit tests.
 *
 * Covers the security-critical paths:
 *   - token storage (sessionStorage, not localStorage)
 *   - isAuthenticated() with valid / expired / absent tokens
 *   - hasRole() / hasAnyRole() RBAC checks
 *   - clearTokens() on logout
 */
describe('AuthService', () => {
  // A minimal valid JWT payload (exp far in the future)
  const futureExp = Math.floor(Date.now() / 1000) + 3600;
  const pastExp   = Math.floor(Date.now() / 1000) - 3600;

  function makeJwt(payload: object): string {
    const header  = btoa(JSON.stringify({ alg: 'HS256', typ: 'JWT' }));
    const body    = btoa(JSON.stringify(payload)).replace(/=/g, '');
    return `${header}.${body}.fakesig`;
  }

  const validPayload = {
    sub: 'user-123',
    tenantId: 'tenant-abc',
    tenantSlug: 'acme',
    roles: [UserRole.ADMIN],
    iat: Math.floor(Date.now() / 1000),
    exp: futureExp,
    iss: 'inventra-api',
    aud: ['inventra-frontend'],
  };

  /**
   * Configure a fresh TestBed and return a freshly-constructed AuthService.
   * Use this when a test relies on the service constructor reading a token
   * out of sessionStorage at instantiation time.
   *
   * The router is configured with a wildcard '/login' route so that
   * AuthService.logout()'s router.navigate(['/login']) does not raise
   * NG04002 (no matching route) as an unhandled rejection.
   */
  function bootstrap(): { service: AuthService; httpMock: HttpTestingController } {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [
        AuthService,
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([{ path: 'login', children: [] }, { path: '**', children: [] }]),
      ],
    });
    return {
      service: TestBed.inject(AuthService),
      httpMock: TestBed.inject(HttpTestingController),
    };
  }

  beforeEach(() => {
    sessionStorage.clear();
  });

  afterEach(() => {
    sessionStorage.clear();
  });

  it('stores tokens in sessionStorage, not localStorage', () => {
    const { service, httpMock } = bootstrap();
    const token: import('../../models').TokenResponse = {
      accessToken:  makeJwt(validPayload),
      refreshToken: 'raw-refresh-token',
      expiresIn:    900,
    };

    service.login({ email: 'a@b.com', password: 'password1' }).subscribe();

    const req = httpMock.expectOne('/api/v1/auth/login');
    req.flush(token);

    const profileReq = httpMock.expectOne(r => r.url.includes('/api/v1/users/'));
    profileReq.flush({ id: 'user-123', role: UserRole.ADMIN, status: UserStatus.ACTIVE,
                       tenantId: 'tenant-abc', email: 'a@b.com',
                       firstName: 'A', lastName: 'B', createdAt: '', updatedAt: '' });

    expect(sessionStorage.getItem('access_token')).toBeTruthy();
    expect(sessionStorage.getItem('refresh_token')).toBe('raw-refresh-token');
    expect(localStorage.getItem('access_token')).toBeNull();
    expect(localStorage.getItem('refresh_token')).toBeNull();
    httpMock.verify();
  });

  it('isAuthenticated() returns false when no token is stored', () => {
    const { service, httpMock } = bootstrap();
    expect(service.isAuthenticated()).toBe(false);
    httpMock.verify();
  });

  it('isAuthenticated() returns true for a valid non-expired token', () => {
    sessionStorage.setItem('access_token', makeJwt(validPayload));
    const { service, httpMock } = bootstrap();
    expect(service.isAuthenticated()).toBe(true);
    httpMock.verify();
  });

  it('isAuthenticated() returns false for an expired token', () => {
    const expiredPayload = { ...validPayload, exp: pastExp };
    sessionStorage.setItem('access_token', makeJwt(expiredPayload));
    const { service, httpMock } = bootstrap();
    expect(service.isAuthenticated()).toBe(false);
    httpMock.verify();
  });

  it('isAuthenticated() returns false for a malformed token', () => {
    sessionStorage.setItem('access_token', 'not.a.jwt');
    const { service, httpMock } = bootstrap();
    expect(service.isAuthenticated()).toBe(false);
    httpMock.verify();
  });

  it('fetches the full profile when rehydrating a session from storage', async () => {
    sessionStorage.setItem('access_token', makeJwt(validPayload));
    const { service, httpMock } = bootstrap();

    // Synchronously, the JWT-derived placeholder keeps guards working…
    expect(service.hasRole(UserRole.ADMIN)).toBe(true);
    expect(service.currentUser()?.firstName).toBe('');

    // …and the profile GET is issued on the following microtask.
    await Promise.resolve();
    const req = httpMock.expectOne('/api/v1/users/user-123');
    req.flush({
      id: 'user-123', tenantId: 'tenant-abc', email: 'demo@demo.com',
      firstName: 'Demo', lastName: 'Admin', role: UserRole.ADMIN,
      status: UserStatus.ACTIVE, createdAt: '', updatedAt: '',
    });

    expect(service.currentUser()?.firstName).toBe('Demo');
    expect(service.currentUser()?.email).toBe('demo@demo.com');
    httpMock.verify();
  });

  it('keeps the JWT-derived user when the rehydration profile fetch fails', async () => {
    sessionStorage.setItem('access_token', makeJwt(validPayload));
    const { service, httpMock } = bootstrap();

    await Promise.resolve();
    httpMock.expectOne('/api/v1/users/user-123')
      .flush({}, { status: 500, statusText: 'Server Error' });

    expect(service.currentUser()?.id).toBe('user-123');
    expect(service.hasRole(UserRole.ADMIN)).toBe(true);
    httpMock.verify();
  });

  it('does not issue a profile fetch when the stored token is expired', async () => {
    sessionStorage.setItem('access_token', makeJwt({ ...validPayload, exp: pastExp }));
    const { httpMock } = bootstrap();

    await Promise.resolve();
    httpMock.verify();
  });

  it('hasRole() returns true when user has the exact role', () => {
    sessionStorage.setItem('access_token', makeJwt(validPayload));
    const { service, httpMock } = bootstrap();
    expect(service.hasRole(UserRole.ADMIN)).toBe(true);
    httpMock.verify();
  });

  it('hasRole() returns false when user has a different role', () => {
    const viewerPayload = { ...validPayload, roles: [UserRole.VIEWER] };
    sessionStorage.setItem('access_token', makeJwt(viewerPayload));
    const { service, httpMock } = bootstrap();
    expect(service.hasRole(UserRole.ADMIN)).toBe(false);
    httpMock.verify();
  });

  it('hasAnyRole() returns true when user has one of the allowed roles', () => {
    sessionStorage.setItem('access_token', makeJwt(validPayload));
    const { service, httpMock } = bootstrap();
    expect(service.hasAnyRole([UserRole.MANAGER, UserRole.ADMIN])).toBe(true);
    httpMock.verify();
  });

  it('hasAnyRole() returns false when user has none of the allowed roles', () => {
    const viewerPayload = { ...validPayload, roles: [UserRole.VIEWER] };
    sessionStorage.setItem('access_token', makeJwt(viewerPayload));
    const { service, httpMock } = bootstrap();
    expect(service.hasAnyRole([UserRole.ADMIN, UserRole.MANAGER])).toBe(false);
    httpMock.verify();
  });

  it('hasAnyRole() returns false when no user is set', () => {
    const { service, httpMock } = bootstrap();
    expect(service.hasAnyRole([UserRole.ADMIN])).toBe(false);
    httpMock.verify();
  });

  it('logout() removes tokens from sessionStorage', () => {
    sessionStorage.setItem('access_token', makeJwt(validPayload));
    sessionStorage.setItem('refresh_token', 'some-refresh');
    const { service, httpMock } = bootstrap();

    service.logout().subscribe();

    const req = httpMock.expectOne('/api/v1/auth/logout');
    req.flush(null, { status: 204, statusText: 'No Content' });

    expect(sessionStorage.getItem('access_token')).toBeNull();
    expect(sessionStorage.getItem('refresh_token')).toBeNull();
    httpMock.verify();
  });

  it('logout() without a refresh token clears state without HTTP call', () => {
    sessionStorage.setItem('access_token', makeJwt(validPayload));
    const { service, httpMock } = bootstrap();

    service.logout().subscribe();

    httpMock.expectNone('/api/v1/auth/logout');
    expect(sessionStorage.getItem('access_token')).toBeNull();
    httpMock.verify();
  });
});
