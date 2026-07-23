# Frontend spec conventions (Vitest)

The frontend runs tests via the Angular unit-test builder, which delegates to **Vitest**.
`vitest/globals` is configured in `tsconfig.spec.json`, so `describe`, `it`, `beforeEach`,
`afterEach`, `expect`, and `vi` are all GLOBAL (no import needed). The only thing you must
import from `'vitest'` is the TYPE `MockedObject` when you need to type a spy object.

The suite was migrated off Jasmine/Karma; no Jasmine globals remain. This document is kept
as the reference for writing new specs and for the non-obvious behavioural differences that
caused real bugs during the migration — in particular the two ⚠️ sections below, both of
which produce **passing tests in a failing run**.

## 1. Jasmine API -> Vitest API

| Jasmine | Vitest replacement |
|---|---|
| `jasmine.createSpyObj('Name', ['a','b'])` | `{ a: vi.fn(), b: vi.fn() }` |
| `jasmine.SpyObj<T>` (type) | `MockedObject<T>` (import type from `'vitest'`) |
| `... as jasmine.SpyObj<T>` | `... as unknown as MockedObject<T>` |
| `spy.and.returnValue(x)` | `spy.mockReturnValue(x)` |
| `spy.and.callFake(fn)` | `spy.mockImplementation(fn)` |
| `spy.and.throwError(e)` | `spy.mockImplementation(() => { throw e; })` |
| `spyOn(obj, 'm')` | `vi.spyOn(obj, 'm').mockImplementation(() => {})` — see warning below |
| `spyOn(obj, 'm').and.returnValue(x)` | `vi.spyOn(obj, 'm').mockReturnValue(x)` |
| `jasmine.any(X)` | `expect.any(X)` |
| `jasmine.objectContaining(x)` | `expect.objectContaining(x)` |
| `jasmine.arrayContaining(x)` | `expect.arrayContaining(x)` |
| `jasmine.stringMatching(x)` | `expect.stringMatching(x)` |

### ⚠️ `vi.spyOn` calls through; `spyOn` did not
This is the single most dangerous difference. Jasmine's `spyOn(obj, 'm')` **replaces** the
method with a no-op stub. Vitest's `vi.spyOn(obj, 'm')` **wraps and still calls the real
method**. A bare `vi.spyOn` is not a stub.

This bites hardest on `Router`. With `provideRouter([])` there are no routes, so a real
`router.navigate([...])` rejects with `NG04002: Cannot match any routes`. Because the
rejection escapes through RxJS rather than the test body, Vitest reports it as an
*unhandled rejection attributed to the file* — the test still passes, and the suite exits
non-zero with no failing test to point at. Always stub navigation:

```ts
vi.spyOn(router, 'navigate').mockResolvedValue(true);
vi.spyOn(router, 'navigateByUrl').mockResolvedValue(true);
```

Put it in the top-level `beforeEach` next to `TestBed.inject(Router)` so every test in the
file is covered, not just the ones that happen to assert on navigation.

### ⚠️ Assertions inside `subscribe` callbacks do not fail tests
`expect(...)` inside an RxJS `next`/`error` handler throws inside the subscriber. Vitest
records it as an unhandled error against the file, not a failure of the test. If a spec
file reports "passed" but the run exits 1, this is why. Prefer asserting on captured values
after the synchronous `flush()`, or use the Promise pattern below.

### `done` callbacks -> Promise
Vitest test context is NOT callable, so `(done) => { ... done(); }` and `done.fail(...)` break.
Convert the whole test to return a Promise:

```ts
// BEFORE
it('handles error', (done) => {
  svc.get('x').subscribe({
    next: () => done.fail('Should have failed'),
    error: (err) => { expect(err.status).toBe(404); done(); },
  });
  const req = httpMock.expectOne('/x');
  req.flush({}, { status: 404, statusText: 'Not Found' });
});

// AFTER
it('handles error', () => new Promise<void>((resolve, reject) => {
  svc.get('x').subscribe({
    next: () => reject(new Error('Should have failed')),
    error: (err) => { expect(err.status).toBe(404); resolve(); },
  });
  const req = httpMock.expectOne('/x');
  req.flush({}, { status: 404, statusText: 'Not Found' });
}));   // <- note the extra closing paren
```

## 2. Data-model alignment

The real model interfaces live in `src/app/models/`. Mock objects must match them exactly.
The list below records the shapes that old specs got wrong — treat it as a reference for
the current contract, not as an outstanding to-do list:

- `Page<T>` (common.model.ts) is FLAT: `{ content, totalElements, totalPages, size, number }`.
  Old specs use a nested `page: { number, size, totalElements, totalPages }`. Flatten it.
  `{ ...mockPage, page: { ...mockPage.page, totalPages: 3 } }` -> `{ ...mockPage, totalPages: 3 }`.
- `Order` (order.model.ts): has `totalAmount`, NOT `subtotal`/`tax`/`total`. Required fields:
  `id, tenantId, orderNumber, customerId, customerName, status, totalAmount, createdBy,
  createdByName, createdAt, updatedAt, items`.
- `OrderItem`: `id, orderId, productId, productName, productSku, quantity, unitPrice, totalPrice`.
  NOT `subtotal`, NOT `product`.
- `Product` (product.model.ts): has `unitPrice`, NOT `costPrice`. Required: `id, tenantId, sku,
  name, unitPrice, status, createdAt, updatedAt`.
- `Customer` (customer.model.ts): NO `city`, `creditLimit`, `currentBalance`. Required: `id,
  tenantId, name, status, createdAt, updatedAt`. Optional: `email, phone, address, notes`.
- `InventoryItem` (inventory.model.ts): use `availableQuantity` (NOT `quantityAvailable`),
  plus `quantityOnHand, quantityReserved, reorderPoint`.
- `AdjustStockRequest` (inventory.model.ts): `{ quantity: number; notes: string }`
  (NOT `quantityChange`/`movementType`/`reason`).

Always open the source component/service under test and the model file to confirm the exact
shape rather than guessing.

## Reference files (follow their style)
- `src/app/core/services/order.service.spec.ts` (service HTTP test reference)
- `src/app/features/orders/order-list/order-list.spec.ts` (component test reference)

## Verification
`npm test` must exit 0. Note that a green test count is not sufficient: check the run for an
`Errors` line, because unhandled errors (see the ⚠️ sections above) do not fail individual
tests but do fail the run.
