import { Routes } from '@angular/router';
import { roleGuard } from '../../core/guards/role.guard';
import { UserRole } from '../../models';
import { ProductListComponent } from './product-list/product-list';
import { ProductFormComponent } from './product-form/product-form';
import { ProductDetailComponent } from './product-detail/product-detail';
import { CategoryTreeComponent } from './category-tree/category-tree';

const PRODUCT_MANAGERS = [UserRole.ADMIN, UserRole.MANAGER];

export const PRODUCTS_ROUTES: Routes = [
  { path: '', component: ProductListComponent, data: { breadcrumb: 'Products' } },
  { path: 'new', component: ProductFormComponent, canActivate: [roleGuard(PRODUCT_MANAGERS)], data: { breadcrumb: 'New Product' } },
  { path: 'categories', component: CategoryTreeComponent, data: { breadcrumb: 'Categories' } },
  { path: ':id', component: ProductDetailComponent, data: { breadcrumb: 'Product Detail' } },
  { path: ':id/edit', component: ProductFormComponent, canActivate: [roleGuard(PRODUCT_MANAGERS)], data: { breadcrumb: 'Edit Product' } }
];
