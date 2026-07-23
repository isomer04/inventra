import { TestBed } from '@angular/core/testing';
import { Router, ActivatedRouteSnapshot, RouterStateSnapshot, UrlTree } from '@angular/router';
import { roleGuard } from './role.guard';
import { AuthService } from '../services/auth.service';
import { ToastService } from '../services/toast.service';
import { UserRole } from '../../models';
import { vi } from 'vitest';

/**
 * roleGuard unit tests.
 *
 * Verifies RBAC enforcement at the routing layer — allows users with
 * the required role, redirects insufficient roles to /dashboard,
 * and redirects unauthenticated users to /login.
 */
describe('roleGuard', () => {
  let authServiceSpy: {
    isAuthenticated: ReturnType<typeof vi.fn>;
    hasAnyRole: ReturnType<typeof vi.fn>;
  };
  let routerSpy: { createUrlTree: ReturnType<typeof vi.fn> };
  let toastSpy: { error: ReturnType<typeof vi.fn> };

  const mockRoute = {} as ActivatedRouteSnapshot;
  const mockState = { url: '/admin' } as RouterStateSnapshot;
  const fakeTree = {} as UrlTree;

  beforeEach(() => {
    authServiceSpy = { isAuthenticated: vi.fn(), hasAnyRole: vi.fn() };
    routerSpy = { createUrlTree: vi.fn().mockReturnValue(fakeTree) };
    toastSpy = { error: vi.fn() };

    TestBed.configureTestingModule({
      providers: [
        { provide: AuthService, useValue: authServiceSpy },
        { provide: Router,      useValue: routerSpy },
        { provide: ToastService, useValue: toastSpy },
      ],
    });
  });

  it('returns true when user is authenticated and has an allowed role', () => {
    authServiceSpy.isAuthenticated.mockReturnValue(true);
    authServiceSpy.hasAnyRole.mockReturnValue(true);

    const guard = roleGuard([UserRole.ADMIN]);
    const result = TestBed.runInInjectionContext(() => guard(mockRoute, mockState));

    expect(result).toBe(true);
    expect(toastSpy.error).not.toHaveBeenCalled();
  });

  it('redirects to /dashboard when authenticated but role is insufficient', () => {
    authServiceSpy.isAuthenticated.mockReturnValue(true);
    authServiceSpy.hasAnyRole.mockReturnValue(false);

    const guard = roleGuard([UserRole.ADMIN]);
    const result = TestBed.runInInjectionContext(() => guard(mockRoute, mockState));

    expect(result).toBe(fakeTree);
    expect(routerSpy.createUrlTree).toHaveBeenCalledWith(['/dashboard']);
    expect(toastSpy.error).toHaveBeenCalled();
  });

  it('redirects to /login when not authenticated', () => {
    authServiceSpy.isAuthenticated.mockReturnValue(false);

    const guard = roleGuard([UserRole.ADMIN]);
    const result = TestBed.runInInjectionContext(() => guard(mockRoute, mockState));

    expect(result).toBe(fakeTree);
    expect(routerSpy.createUrlTree).toHaveBeenCalledWith(
      ['/login'],
      { queryParams: { returnUrl: '/admin' } }
    );
  });
});
