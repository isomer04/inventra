import { Page } from './common.model';

export enum CustomerStatus {
  ACTIVE = 'ACTIVE',
  INACTIVE = 'INACTIVE'
}

export interface Customer {
  id: string;
  tenantId: string;
  name: string;
  email?: string;
  phone?: string;
  address?: string;
  notes?: string;
  status: CustomerStatus;
  createdAt: string;
  updatedAt: string;
}

export interface CreateCustomerRequest {
  name: string;
  email?: string;
  phone?: string;
  address?: string;
  notes?: string;
}

export interface UpdateCustomerRequest {
  name?: string;
  email?: string;
  phone?: string;
  address?: string;
  notes?: string;
  status?: CustomerStatus;
}

export type CustomerCreateRequest = CreateCustomerRequest;
export type CustomerUpdateRequest = UpdateCustomerRequest;

export type CustomerPage = Page<Customer>;
