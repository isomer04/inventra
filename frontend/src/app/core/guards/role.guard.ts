import { inject } from '@angular/core';
import { Router, CanActivateFn, UrlTree } from '@angular/router';
import { AuthService } from '../services/auth.service';
import { UserRole } from '../../models';
import { ToastService } from '../services/toast.service';

export const roleGuard = (allowedRoles: UserRole[]): CanActivateFn => {
  return (route, state): boolean | UrlTree => {
    const authService = inject(AuthService);
    const router = inject(Router);
    const toastService = inject(ToastService);
    
    if (!authService.isAuthenticated()) {
      return router.createUrlTree(['/login'], { queryParams: { returnUrl: state.url } });
    }
    
    if (authService.hasAnyRole(allowedRoles)) {
      return true;
    }
    
    toastService.error('Access Denied. You do not have permission to access this page.');
    return router.createUrlTree(['/dashboard']);
  };
};
