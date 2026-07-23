import { Routes } from '@angular/router';
import { authGuard } from './core/guards/auth.guard';
import { noAuthGuard } from './core/guards/no-auth.guard';
import { LoginComponent } from './features/auth/login/login';
import { SignupComponent } from './features/auth/signup/signup';
import { DashboardComponent } from './features/dashboard/dashboard';
import { MainLayoutComponent } from './shared/components/main-layout/main-layout';

export const routes: Routes = [
  { path: 'login', component: LoginComponent, canActivate: [noAuthGuard] },
  { path: 'signup', component: SignupComponent, canActivate: [noAuthGuard] },
  {
    path: '',
    component: MainLayoutComponent,
    canActivate: [authGuard],
    children: [
      { path: 'dashboard', component: DashboardComponent, data: { breadcrumb: 'Dashboard' } },
      {
        path: 'products',
        data: { breadcrumb: 'Products' },
        loadChildren: () => import('./features/products/products.routes').then(m => m.PRODUCTS_ROUTES)
      },
      {
        path: 'inventory',
        data: { breadcrumb: 'Inventory' },
        loadChildren: () => import('./features/inventory/inventory.routes').then(m => m.INVENTORY_ROUTES)
      },
      {
        path: 'orders',
        data: { breadcrumb: 'Orders' },
        loadChildren: () => import('./features/orders/orders.routes').then(m => m.ORDERS_ROUTES)
      },
      {
        path: 'customers',
        data: { breadcrumb: 'Customers' },
        loadChildren: () => import('./features/customers/customers.routes').then(m => m.CUSTOMERS_ROUTES)
      },
      {
        path: 'reports',
        data: { breadcrumb: 'Reports' },
        loadChildren: () => import('./features/reports/reports.routes').then(m => m.REPORTS_ROUTES)
      },
      { path: '', redirectTo: '/dashboard', pathMatch: 'full' }
    ]
  },
  { path: '**', redirectTo: '/dashboard' }
];
