import { Routes } from '@angular/router';
import { roleGuard } from '../../core/guards/role.guard';
import { UserRole } from '../../models';
import { StockLevelsComponent } from './stock-levels/stock-levels';
import { MovementHistoryComponent } from './movement-history/movement-history';

export const INVENTORY_ROUTES: Routes = [
  { path: '', component: StockLevelsComponent, data: { breadcrumb: 'Stock Levels' } },
  {
    path: 'movements',
    component: MovementHistoryComponent,
    canActivate: [roleGuard([UserRole.ADMIN, UserRole.MANAGER])],
    data: { breadcrumb: 'Movement History' }
  }
];
