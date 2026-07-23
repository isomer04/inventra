import { HttpContextToken } from '@angular/common/http';

/**
 * Set this token to true on requests that handle their own errors
 * (e.g. via catchError in the service layer) so the global error
 * interceptor does not show a duplicate toast.
 *
 * Usage:
 *   this.http.get('/api/...', { context: new HttpContext().set(SILENT_ERROR, true) })
 */
export const SILENT_ERROR = new HttpContextToken<boolean>(() => false);
