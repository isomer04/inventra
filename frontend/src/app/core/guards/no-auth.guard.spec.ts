import { TestBed } from '@angular/core/testing';
import { Router, ActivatedRouteSnapshot, RouterStateSnapshot, UrlTree } from '@angular/router';
import { noAuthGuard } from './no-auth.guard';
import { AuthService } from '../services/auth.service';
import { vi } from 'vitest';

/**
 * noAuthGuard unit tests.
 *
 * Verifies that the guard prevents authenticated users from accessing
 * public-only pages (login, signup) and redirects them to the dashboard.
 */
describe('noAuthGuard', () => {
  let authServiceSpy: { isAuthenticated: ReturnType<typeof vi.fn> };
  let routerSpy: { createUrlTree: ReturnType<typeof vi.fn> };

  const mockRoute = {} as ActivatedRouteSnapshot;
  const mockState = { url: '/login' } as RouterStateSnapshot;

  beforeEach(() => {
    authServiceSpy = { isAuthenticated: vi.fn() };
    routerSpy = { createUrlTree: vi.fn() };

    TestBed.configureTestingModule({
      providers: [
        { provide: AuthService, useValue: authServiceSpy },
        { provide: Router, useValue: routerSpy },
      ],
    });
  });

  it('allows access when user is not authenticated', () => {
    authServiceSpy.isAuthenticated.mockReturnValue(false);

    const result = TestBed.runInInjectionContext(() =>
      noAuthGuard(mockRoute, mockState)
    );

    expect(result).toBe(true);
    expect(routerSpy.createUrlTree).not.toHaveBeenCalled();
  });

  it('redirects to dashboard when user is authenticated', () => {
    authServiceSpy.isAuthenticated.mockReturnValue(true);
    const fakeTree = {} as UrlTree;
    routerSpy.createUrlTree.mockReturnValue(fakeTree);

    const result = TestBed.runInInjectionContext(() =>
      noAuthGuard(mockRoute, mockState)
    );

    expect(result).toBe(fakeTree);
    expect(routerSpy.createUrlTree).toHaveBeenCalledWith(['/dashboard']);
  });

  it('redirects to dashboard from login page', () => {
    authServiceSpy.isAuthenticated.mockReturnValue(true);
    const fakeTree = {} as UrlTree;
    routerSpy.createUrlTree.mockReturnValue(fakeTree);
    const loginState = { url: '/login' } as RouterStateSnapshot;

    const result = TestBed.runInInjectionContext(() =>
      noAuthGuard(mockRoute, loginState)
    );

    expect(result).toBe(fakeTree);
    expect(routerSpy.createUrlTree).toHaveBeenCalledWith(['/dashboard']);
  });

  it('redirects to dashboard from signup page', () => {
    authServiceSpy.isAuthenticated.mockReturnValue(true);
    const fakeTree = {} as UrlTree;
    routerSpy.createUrlTree.mockReturnValue(fakeTree);
    const signupState = { url: '/signup' } as RouterStateSnapshot;

    const result = TestBed.runInInjectionContext(() =>
      noAuthGuard(mockRoute, signupState)
    );

    expect(result).toBe(fakeTree);
    expect(routerSpy.createUrlTree).toHaveBeenCalledWith(['/dashboard']);
  });

  it('allows unauthenticated access to login page', () => {
    authServiceSpy.isAuthenticated.mockReturnValue(false);
    const loginState = { url: '/login' } as RouterStateSnapshot;

    const result = TestBed.runInInjectionContext(() =>
      noAuthGuard(mockRoute, loginState)
    );

    expect(result).toBe(true);
  });

  it('allows unauthenticated access to signup page', () => {
    authServiceSpy.isAuthenticated.mockReturnValue(false);
    const signupState = { url: '/signup' } as RouterStateSnapshot;

    const result = TestBed.runInInjectionContext(() =>
      noAuthGuard(mockRoute, signupState)
    );

    expect(result).toBe(true);
  });
});
