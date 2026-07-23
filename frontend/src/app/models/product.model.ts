import { Page } from './common.model';

export enum ProductStatus {
  ACTIVE = 'ACTIVE',
  DISCONTINUED = 'DISCONTINUED'
}

export interface Product {
  id: string;
  tenantId: string;
  sku: string;
  name: string;
  description?: string;
  categoryId?: string;
  categoryName?: string;
  unitPrice: number;
  unitOfMeasure?: string;
  status: ProductStatus;
  createdAt: string;
  updatedAt: string;
}

export interface CreateProductRequest {
  sku: string;
  name: string;
  description?: string;
  categoryId?: string;
  unitPrice: number;
  unitOfMeasure?: string;
}

export interface UpdateProductRequest {
  sku?: string;
  name?: string;
  description?: string;
  categoryId?: string;
  unitPrice?: number;
  unitOfMeasure?: string;
  status?: ProductStatus;
}

export type ProductCreateRequest = CreateProductRequest;
export type ProductUpdateRequest = UpdateProductRequest;

export type ProductPage = Page<Product>;
