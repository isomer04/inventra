import { http, HttpResponse, delay } from 'msw';
import { setupServer } from 'msw/node';
import { beforeAll, afterEach, afterAll } from 'vitest';

const API_BASE = 'http://localhost:8080/api/v1';

export const handlers = [
  http.get(`${API_BASE}/products`, () => {
    return HttpResponse.json({
      content: [
        { id: 1, name: 'Product 1', sku: 'SKU001', unitPrice: 10.00 },
        { id: 2, name: 'Product 2', sku: 'SKU002', unitPrice: 20.00 },
      ],
      totalElements: 2,
      totalPages: 1,
    });
  }),

  http.post(`${API_BASE}/orders`, () => {
    return HttpResponse.json({
      id: 1,
      orderNumber: 'ORD-001',
      status: 'SUBMITTED',
    }, { status: 201 });
  }),
];

export const server = setupServer(...handlers);

export const networkErrorHandlers = {
  offline: () => http.get(`${API_BASE}/products`, () => {
    return HttpResponse.error();
  }),

  offlineOrders: () => http.post(`${API_BASE}/orders`, () => {
    return HttpResponse.error();
  }),

  slow: () => http.get(`${API_BASE}/products`, async () => {
    await delay(5000);
    return HttpResponse.json({ content: [], totalElements: 0 });
  }),

  // Must exceed the HTTP client timeout to exercise timeout handling.
  timeout: () => http.get(`${API_BASE}/products`, async () => {
    await delay(35000);
    return HttpResponse.json({ content: [], totalElements: 0 });
  }),

  serverError: () => http.get(`${API_BASE}/products`, () => {
    return HttpResponse.json(
      { message: 'Internal server error' },
      { status: 500 }
    );
  }),

  serviceUnavailable: () => http.get(`${API_BASE}/products`, () => {
    return HttpResponse.json(
      { message: 'Service temporarily unavailable' },
      { status: 503 }
    );
  }),
};

export function setupMSW() {
  beforeAll(() => server.listen({ onUnhandledRequest: 'warn' }));

  afterEach(() => server.resetHandlers());

  afterAll(() => server.close());
}
