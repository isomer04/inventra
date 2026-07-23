import { Routes } from '@angular/router';
import { roleGuard } from '../../core/guards/role.guard';
import { UserRole } from '../../models';
import { OrderListComponent } from './order-list/order-list';
import { OrderFormComponent } from './order-form/order-form';
import { OrderDetailComponent } from './order-detail/order-detail';

const ORDER_CREATORS = [UserRole.ADMIN, UserRole.MANAGER, UserRole.WAREHOUSE_STAFF];
const ORDER_MANAGERS = [UserRole.ADMIN, UserRole.MANAGER];

export const ORDERS_ROUTES: Routes = [
  { path: '', component: OrderListComponent },
  { path: 'new', component: OrderFormComponent, canActivate: [roleGuard(ORDER_CREATORS)] },
  { path: ':id', component: OrderDetailComponent },
  { path: ':id/edit', component: OrderFormComponent, canActivate: [roleGuard(ORDER_MANAGERS)] }
];
