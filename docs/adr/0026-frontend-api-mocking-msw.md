+++
adr = "0026"

[[covers]]
id = "msw"
version = "^2.14.6"
manifest = "frontend/package.json"
+++

# ADR-0026: Frontend API Mocking: Mock Service Worker (MSW)

## Status
Accepted

## Context
Inventra's frontend requires a robust API mocking solution for testing and development. The application makes HTTP requests to backend REST APIs for inventory management, order processing, user authentication, and tenant operations. Without API mocking, frontend tests would require:

- **Running backend services** during test execution, creating slow, brittle tests that depend on external systems
- **Network connectivity** to staging or development environments, making tests unreliable and impossible to run offline
- **Test data management** across multiple services, increasing test complexity and maintenance burden
- **Flaky tests** due to network latency, service downtime, or race conditions in asynchronous operations

The mocking solution must support:

- **HTTP interception** at the network level, allowing tests to intercept fetch/XMLHttpRequest calls without modifying application code
- **Request matching** based on URL patterns, HTTP methods, headers, and request bodies to route requests to appropriate mock handlers
- **Response simulation** including status codes, headers, response bodies, and network delays to test error handling and loading states
- **TypeScript integration** with type-safe request/response definitions aligned with backend API contracts
- **Development mode** to mock APIs during local development when backend services are unavailable or under development
- **Test isolation** ensuring each test can define its own mock handlers without affecting other tests

Mock Service Worker (MSW) version `^2.14.6` provides network-level API mocking by intercepting requests at the fetch/XMLHttpRequest layer. Unlike traditional mocking libraries that stub HTTP clients or inject mock data into components, MSW intercepts requests at the network boundary, allowing the application code to remain unchanged. This approach ensures tests exercise the same code paths as production, including HTTP client configuration, request interceptors, and error handling logic.

MSW uses Service Workers in the browser and Node.js request interception in test environments, providing a unified API for both development and testing. Version 2.14.6 is the latest stable release, offering improved TypeScript support, better error messages, and enhanced request matching capabilities. The caret (`^`) range allows minor and patch updates within version 2.

Source manifest: `frontend/package.json`, `devDependencies` section.

## Decision
Use **Mock Service Worker (MSW)** version `^2.14.6` for frontend API mocking in tests and development.

Source manifest: `frontend/package.json`, `devDependencies` section.

## Consequences
- **Gained (network-level interception):** MSW intercepts HTTP requests at the network boundary (fetch/XMLHttpRequest), allowing tests to exercise the full request/response cycle including HTTP client configuration, request interceptors, authentication headers, and error handling. This ensures tests validate the same code paths as production, catching integration issues that component-level mocks would miss.
- **Gained (test realism):** Because MSW operates at the network level, application code remains unchanged — components, services, and HTTP clients function identically in tests and production. This eliminates the risk of tests passing with mocked dependencies but failing in production due to incorrect mock implementations.
- **Gained (development mode support):** MSW can run in the browser during local development, intercepting API requests and returning mock responses. This allows frontend developers to work independently of backend services, unblocking development when backend APIs are unavailable, under development, or experiencing downtime.
- **Gained (request matching flexibility):** MSW provides powerful request matching based on URL patterns (exact, wildcard, regex), HTTP methods, headers, query parameters, and request bodies. This allows precise control over which requests are mocked and which pass through to real services, enabling hybrid testing scenarios.
- **Gained (response simulation):** MSW can simulate various response scenarios including success responses, error responses (4xx, 5xx), network delays, timeouts, and streaming responses. This enables comprehensive testing of loading states, error handling, retry logic, and edge cases without requiring backend changes.
- **Gained (TypeScript integration):** MSW supports TypeScript with type-safe request/response definitions. Mock handlers can be typed to match backend API contracts, ensuring mock responses conform to expected shapes and catching type mismatches at compile time.
- **Gained (test isolation):** MSW allows defining mock handlers per-test or per-suite, ensuring each test has isolated mock state. Handlers can be dynamically added, removed, or overridden during test execution, enabling fine-grained control over mock behavior.
- **Cost (learning curve):** MSW introduces a new API for defining request handlers, response resolvers, and request matching patterns. Developers unfamiliar with MSW need to learn its concepts (handlers, resolvers, context utilities) and best practices for organizing mock definitions. This adds 1-2 days to onboarding time.
- **Cost (setup complexity):** MSW requires initial setup to configure the mock server for Node.js tests and the Service Worker for browser-based development. This includes creating handler definitions, configuring the server lifecycle (start/stop), and integrating with the test framework (Vitest). The setup is more complex than simple function mocks.
- **Cost (maintenance burden):** Mock handlers must be kept in sync with backend API contracts. When backend APIs change (new endpoints, modified request/response shapes, different status codes), corresponding MSW handlers must be updated. Without automated contract testing, this synchronization is manual and error-prone.
- **Mitigation (learning curve):** MSW's documentation provides clear examples for common use cases (REST APIs, GraphQL, authentication flows). The team can create reusable handler factories for common API patterns (paginated lists, CRUD operations, error responses) to reduce duplication and simplify handler definitions.
- **Mitigation (setup complexity):** MSW provides setup utilities for popular test frameworks including Vitest. The team can create a shared test setup file that configures MSW once and reuses it across all test files, minimizing per-test boilerplate.
- **Mitigation (maintenance burden):** The team can co-locate MSW handlers with API client code, making it easier to update handlers when API contracts change. TypeScript types shared between frontend and backend (via OpenAPI code generation or shared type packages) can ensure mock responses match backend contracts at compile time.

### Alternatives considered
- **Angular HttpClientTestingModule** — Angular's built-in HTTP testing module provides a mock HTTP backend for testing services that use `HttpClient`. However, it requires injecting the mock backend into tests and manually verifying requests/responses, creating verbose test code. It also only works with Angular's `HttpClient`, not with fetch-based libraries or third-party HTTP clients. Rejected because it is Angular-specific, verbose, and does not support development mode mocking.
- **Nock** — A popular HTTP mocking library for Node.js that intercepts HTTP requests at the `http`/`https` module level. However, Nock only works in Node.js environments, not in browsers, making it unsuitable for development mode mocking. It also has a different API for Node.js vs. browser environments, requiring separate mock definitions. Rejected because it lacks browser support and unified API.
- **Mirage JS** — A client-side API mocking library that provides an in-memory database and route handlers for simulating backend APIs. While Mirage offers powerful features like relationships, serializers, and factories, it is more complex than MSW and requires defining a full data model. For Inventra's use case (simple request/response mocking), Mirage's complexity is unnecessary. Rejected due to higher complexity and steeper learning curve.
- **JSON Server** — A simple tool that creates a REST API from a JSON file, useful for prototyping. However, JSON Server requires running a separate server process, adding complexity to test setup and CI/CD pipelines. It also lacks request matching flexibility and cannot simulate error responses or network delays without custom middleware. Rejected because it requires a separate server process and lacks advanced mocking features.
- **Manual fetch mocking** — Directly mocking the global `fetch` function or `XMLHttpRequest` in tests using Jest/Vitest mocks. While this approach requires no additional dependencies, it is verbose, error-prone, and difficult to maintain. Each test must manually set up request/response mocks, leading to duplicated code. It also does not support development mode mocking. Rejected due to verbosity and lack of reusability.

### References
- https://mswjs.io/
- https://mswjs.io/docs/getting-started
- https://mswjs.io/docs/integrations/node
- https://mswjs.io/docs/best-practices/typescript
- https://github.com/mswjs/msw

