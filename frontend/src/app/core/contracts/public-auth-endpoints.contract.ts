/**
 * Frontend mirror of backend SecurityPaths.PUBLIC_AUTH_ENDPOINTS.
 *
 * Java constants cannot be imported into this bundle. CI runs
 * scripts/verify-public-auth-contract.py to prevent the two contracts drifting.
 */
export const PUBLIC_AUTH_ENDPOINTS = [
  '/api/v1/auth/register',
  '/api/v1/auth/login',
  '/api/v1/auth/refresh',
  '/api/v1/auth/logout',
] as const;
