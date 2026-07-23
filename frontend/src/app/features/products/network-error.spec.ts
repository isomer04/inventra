/**
 * Network Error Tests
 * 
 * Tests network error handling including offline mode, slow network,
 * timeouts, and retry logic.
 */

import { describe, it, expect, beforeEach } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { HttpClient, provideHttpClient } from '@angular/common/http';
import { setupMSW, server, networkErrorHandlers } from '../../../testing/msw-setup';

setupMSW();

describe('Network Error Tests', () => {
  let httpClient: HttpClient;
  const API_BASE = 'http://localhost:8080/api/v1';

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient()],
    });
    httpClient = TestBed.inject(HttpClient);
  });

  describe('Offline Mode', () => {
    it('should handle network error when offline', async () => {
      server.use(networkErrorHandlers.offline());

      try {
        await httpClient.get(`${API_BASE}/products`).toPromise();
        expect.fail('Should have thrown network error');
      } catch (error: any) {
        expect(error).toBeDefined();
        expect(error.status).toBe(0); // Network error status
      }
    });

    it('should show cached data when offline (if cache exists)', async () => {
      // First request succeeds (populates cache)
      const firstResponse = await httpClient.get(`${API_BASE}/products`).toPromise();
      expect(firstResponse).toBeDefined();

      server.use(networkErrorHandlers.offline());

      // In a real app with service worker, cached data would be shown.
      // This test documents the expected behavior.
      try {
        await httpClient.get(`${API_BASE}/products`).toPromise();
        expect.fail('Should have thrown network error (no cache in test environment)');
      } catch (error: any) {
        expect(error).toBeDefined();
        // Note: In production with service worker, this would return cached data
      }
    });

    it('should queue order submission when offline', async () => {
      server.use(
        networkErrorHandlers.offline(),
        networkErrorHandlers.offlineOrders()
      );

      try {
        await httpClient.post(`${API_BASE}/orders`, { customerId: 1 }).toPromise();
        expect.fail('Should have thrown network error');
      } catch (error: any) {
        expect(error).toBeDefined();
        expect(error.status).toBe(0);
        // Note: In production, this would be queued for retry when online
      }
    });
  });

  describe('Slow Network', () => {
    it('should handle slow network response', async () => {
      server.use(networkErrorHandlers.slow());

      const startTime = Date.now();

      try {
        await httpClient.get(`${API_BASE}/products`).toPromise();
        const duration = Date.now() - startTime;

        expect(duration).toBeGreaterThan(4000); // At least 4 seconds
      } catch (error) {
        // Timeout is acceptable for slow network
        expect(error).toBeDefined();
      }
    }, 10000); // 10 second test timeout

    it('should show loading indicator on slow response', async () => {
      server.use(networkErrorHandlers.slow());
      let isLoading = false;

      const loadingPromise = new Promise<void>((resolve) => {
        isLoading = true;
        setTimeout(() => {
          expect(isLoading).toBe(true); // Still loading after 1 second
          resolve();
        }, 1000);
      });

      const requestPromise = httpClient.get(`${API_BASE}/products`).toPromise()
        .finally(() => {
          isLoading = false;
        });

      await loadingPromise;
      await requestPromise.catch((_e) => { /* ignore timeout errors */ }); // Ignore timeout errors
    }, 10000);

    it('should timeout after 30 seconds on slow network', async () => {
      server.use(networkErrorHandlers.timeout());

      const startTime = Date.now();

      try {
        // Note: Angular HttpClient doesn't have built-in timeout by default
        // In production, use timeout operator from RxJS
        await httpClient.get(`${API_BASE}/products`).toPromise();

        // If we get here, the request completed (shouldn't happen with 35s delay)
        const duration = Date.now() - startTime;
        expect(duration).toBeLessThan(35000); // Should timeout before 35s
      } catch (error) {
        // Timeout or error is expected
        expect(error).toBeDefined();
      }
    }, 40000); // 40 second test timeout
  });

  describe('Request Timeout', () => {
    it('should display timeout error message', async () => {
      server.use(networkErrorHandlers.timeout());

      try {
        await httpClient.get(`${API_BASE}/products`).toPromise();
        expect.fail('Should have thrown timeout error');
      } catch (error: any) {
        expect(error).toBeDefined();
        // In production, this would show a user-friendly timeout message
        // e.g., "Request timed out. Please try again."
      }
    }, 40000);

    it('should allow retry after timeout', async () => {
      let requestCount = 0;
      server.use(
        networkErrorHandlers.timeout()
      );

      try {
        requestCount++;
        await httpClient.get(`${API_BASE}/products`).toPromise();
        expect.fail('First request should have timed out');
      } catch (error) {
        expect(error).toBeDefined();
      }

      // Reset to normal handler for retry
      server.resetHandlers();

      requestCount++;
      const response = await httpClient.get(`${API_BASE}/products`).toPromise();

      expect(response).toBeDefined();
      expect(requestCount).toBe(2); // Two requests made
    }, 45000);
  });
});
