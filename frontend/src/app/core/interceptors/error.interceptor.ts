import { HttpInterceptorFn, HttpErrorResponse } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';
import { ToastService } from '../services/toast.service';
import { ApiError, FieldViolation } from '../../models';
import { SILENT_ERROR } from './http-context-tokens';

function extractApiError(errorObj: object): Partial<ApiError> | null {
  const result: Partial<ApiError> = {};
  
  if ('message' in errorObj && typeof errorObj.message === 'string') {
    result.message = errorObj.message;
  }
  
  if ('status' in errorObj && typeof errorObj.status === 'number') {
    result.status = errorObj.status;
  }
  
  if ('timestamp' in errorObj && typeof errorObj.timestamp === 'string') {
    result.timestamp = errorObj.timestamp;
  }
  
  if ('error' in errorObj && typeof errorObj.error === 'string') {
    result.error = errorObj.error;
  }

  // The backend sends `violations: [{field, message}]`. Each entry is validated
  // individually so one malformed element cannot discard the rest.
  if ('violations' in errorObj && Array.isArray(errorObj.violations)) {
    const violations: FieldViolation[] = errorObj.violations
      .filter(
        (v: unknown): v is FieldViolation =>
          typeof v === 'object' &&
          v !== null &&
          'field' in v &&
          'message' in v &&
          typeof (v as FieldViolation).field === 'string' &&
          typeof (v as FieldViolation).message === 'string'
      );

    if (violations.length > 0) {
      result.violations = violations;
    }
  }

  return Object.keys(result).length > 0 ? result : null;
}

export const errorInterceptor: HttpInterceptorFn = (req, next) => {
  const toastService = inject(ToastService);
  
  return next(req).pipe(
    catchError((error: HttpErrorResponse) => {
      let errorMessage: string;
      
      if (error.error instanceof ErrorEvent) {
        errorMessage = `Error: ${error.error.message}`;
      } else {
        const apiError: Partial<ApiError> | null = 
          error.error && typeof error.error === 'object' 
            ? extractApiError(error.error)
            : null;
        
        switch (error.status) {
          case 400:
            if (apiError?.violations?.length) {
              // Name the offending field — the generic "Validation failed" the backend
              // puts in `message` tells the user nothing actionable on its own.
              const errors = apiError.violations
                .map(v => `${v.field}: ${v.message}`)
                .join(', ');
              errorMessage = `Validation Error: ${errors}`;
            } else {
              errorMessage = apiError?.message || 'Bad Request';
            }
            break;
          case 401:
            errorMessage = 'Unauthorized. Please login again.';
            break;
          case 403:
            errorMessage = 'Access Denied. You do not have permission to perform this action.';
            break;
          case 404:
            errorMessage = apiError?.message || 'Resource not found';
            break;
          case 409:
            errorMessage = apiError?.message || 'Conflict. Resource already exists.';
            break;
          case 500:
            errorMessage = 'Server Error. Please try again later.';
            break;
          default:
            errorMessage = apiError?.message || `Error: ${error.status}`;
        }
      }
      
      // Show error message (except for 401 which is handled by auth interceptor,
      // and requests that opted out via SILENT_ERROR context token)
      if (error.status !== 401 && !req.context.get(SILENT_ERROR)) {
        toastService.error(errorMessage);
      }
      
      return throwError(() => error);
    })
  );
};
