import { TestBed } from '@angular/core/testing';
import { Router, ActivatedRouteSnapshot, RouterStateSnapshot, UrlTree } from '@angular/router';
import { authGuard } from './auth.guard';
import { AuthService } from '../services/auth.service';
import { vi } from 'vitest';

/**
 * authGuard unit tests.
 *
 * Verifies that the guard correctly allows authenticated users and
 * redirects unauthenticated users to /login with the returnUrl query parameter.
 */
describe('authGuard', () => {
  let authServiceSpy: { isAuthenticated: ReturnType<typeof vi.fn> };
  let routerSpy: { createUrlTree: ReturnType<typeof vi.fn> };

  const mockRoute = {} as ActivatedRouteSnapshot;
  const mockState = { url: '/dashboard' } as RouterStateSnapshot;

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

  it('returns true when user is authenticated', () => {
    authServiceSpy.isAuthenticated.mockReturnValue(true);

    const result = TestBed.runInInjectionContext(() =>
      authGuard(mockRoute, mockState)
    );

    expect(result).toBe(true);
    expect(routerSpy.createUrlTree).not.toHaveBeenCalled();
  });

  it('redirects to /login with returnUrl when unauthenticated', () => {
    authServiceSpy.isAuthenticated.mockReturnValue(false);
    const fakeTree = {} as UrlTree;
    routerSpy.createUrlTree.mockReturnValue(fakeTree);

    const result = TestBed.runInInjectionContext(() =>
      authGuard(mockRoute, mockState)
    );

    expect(result).toBe(fakeTree);
    expect(routerSpy.createUrlTree).toHaveBeenCalledWith(
      ['/login'],
      { queryParams: { returnUrl: '/dashboard' } }
    );
  });
});
