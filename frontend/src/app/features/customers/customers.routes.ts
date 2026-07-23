import { Routes } from '@angular/router';
import { roleGuard } from '../../core/guards/role.guard';
import { UserRole } from '../../models';
import { CustomerListComponent } from './customer-list/customer-list';
import { CustomerFormComponent } from './customer-form/customer-form';
import { CustomerDetailComponent } from './customer-detail/customer-detail';

const CUSTOMER_MANAGERS = [UserRole.ADMIN, UserRole.MANAGER];

export const CUSTOMERS_ROUTES: Routes = [
  { path: '', component: CustomerListComponent },
  { path: 'new', component: CustomerFormComponent, canActivate: [roleGuard(CUSTOMER_MANAGERS)] },
  { path: ':id', component: CustomerDetailComponent },
  { path: ':id/edit', component: CustomerFormComponent, canActivate: [roleGuard(CUSTOMER_MANAGERS)] }
];
