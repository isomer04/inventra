# Inventra Frontend

Angular 21 SPA for the Inventra multi-tenant inventory and order management system.

## Prerequisites

- Node.js 22+ and npm 11+
- Angular CLI 21+
- Backend API running (see root README for Docker Compose setup)

## Installation

```bash
npm install
```

## Development Server

```bash
npm start
# App: http://localhost:4200
# API calls are proxied to http://localhost:8081 via proxy.conf.json
```

## Build

```bash
npm run build:prod   # production build → dist/
```

## Testing

```bash
npm test             # Vitest via @angular/build:unit-test
npm run lint         # ESLint
```

## Project Structure

```
src/
├── app/
│   ├── core/              # Singleton services, guards, interceptors
│   │   ├── guards/        # Auth and role guards
│   │   ├── interceptors/  # HTTP interceptors (auth, error handling)
│   │   └── services/      # Core services
│   ├── shared/            # Shared components, pipes, directives
│   ├── features/          # Feature modules
│   │   ├── auth/          # Login
│   │   ├── dashboard/     # Dashboard with widgets
│   │   ├── products/      # Product management
│   │   ├── categories/    # Category management
│   │   ├── inventory/     # Inventory and stock movements
│   │   ├── orders/        # Full order lifecycle
│   │   ├── customers/     # Customer management
│   │   └── reports/       # Inventory, movement, order, top-products reports
│   └── models/            # TypeScript interfaces for all DTOs
└── styles.scss
```

## Features

- JWT authentication with automatic token refresh on 401
- Tokens stored in `sessionStorage` (cleared on tab close)
- Role-based UI rendering (ADMIN, MANAGER, WAREHOUSE_STAFF, VIEWER)
- Full order lifecycle UI: DRAFT → SUBMITTED → APPROVED/REJECTED → PICKING → SHIPPED → DELIVERED
- Chart.js reports for inventory summary, stock movements, and top products
- Accessibility tests via axe-core

## Demo Credentials (dev profile only)

```
Email:    admin@demo.com
Password: demo1234
```

## License

MIT — see [LICENSE](../LICENSE).
