import { HttpInterceptorFn, HttpErrorResponse } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, switchMap, throwError } from 'rxjs';
import { AuthService } from '../services/auth.service';
import { Router } from '@angular/router';
import { PUBLIC_AUTH_ENDPOINTS } from '../contracts/public-auth-endpoints.contract';

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const authService = inject(AuthService);
  const router = inject(Router);
  const requestPath = req.url.split('?', 1)[0] ?? req.url;
  const isAuthEndpoint = PUBLIC_AUTH_ENDPOINTS.some(endpoint => requestPath.endsWith(endpoint));

  if (isAuthEndpoint) {
    return next(req);
  }

  const token = authService.getAccessToken();
  if (token) {
    req = req.clone({
      setHeaders: {
        Authorization: `Bearer ${token}`
      }
    });
  }

  return next(req).pipe(
    catchError((error: HttpErrorResponse) => {
      if (error.status !== 401) {
        return throwError(() => error);
      }

      // Keep refresh failure handling scoped to the refresh request. If the refresh
      // succeeds but the retried business request returns 403/404/500, that error must
      // propagate without destroying an otherwise valid session.
      const refreshRequest$ = authService.refreshToken().pipe(
        catchError((refreshError: unknown) => {
          authService.clearSession();
          router.navigate(['/login']);
          return throwError(() => refreshError);
        })
      );

      return refreshRequest$.pipe(
        switchMap(() => {
          const newToken = authService.getAccessToken();
          if (!newToken) {
            authService.clearSession();
            router.navigate(['/login']);
            return throwError(() => error);
          }

          return next(req.clone({
            setHeaders: {
              Authorization: `Bearer ${newToken}`
            }
          }));
        })
      );
    })
  );
};
