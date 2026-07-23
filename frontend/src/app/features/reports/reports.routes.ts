import { Routes } from '@angular/router';
import { roleGuard } from '../../core/guards/role.guard';
import { UserRole } from '../../models';
import { ReportsDashboardComponent } from './reports-dashboard/reports-dashboard';
import { InventorySummaryComponent } from './inventory-summary/inventory-summary';
import { StockMovementsReportComponent } from './stock-movements-report/stock-movements-report';
import { OrderSummaryReportComponent } from './order-summary-report/order-summary-report';
import { TopProductsReportComponent } from './top-products-report/top-products-report';

export const REPORTS_ROUTES: Routes = [
  { path: '', component: ReportsDashboardComponent },
  { path: 'inventory', component: InventorySummaryComponent },
  {
    path: 'stock-movements',
    component: StockMovementsReportComponent,
    canActivate: [roleGuard([UserRole.ADMIN, UserRole.MANAGER])]
  },
  { path: 'orders', component: OrderSummaryReportComponent },
  { path: 'top-products', component: TopProductsReportComponent }
];
