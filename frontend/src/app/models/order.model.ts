import { Page } from './common.model';

export enum OrderStatus {
  DRAFT = 'DRAFT',
  SUBMITTED = 'SUBMITTED',
  APPROVED = 'APPROVED',
  PICKING = 'PICKING',
  SHIPPED = 'SHIPPED',
  DELIVERED = 'DELIVERED',
  REJECTED = 'REJECTED',
  CANCELLED = 'CANCELLED'
}

export interface Order {
  id: string;
  tenantId: string;
  orderNumber: string;
  customerId: string;
  customerName: string;
  status: OrderStatus;
  totalAmount: number;
  notes?: string;
  createdBy: string;
  createdByName: string;
  createdAt: string;
  updatedAt: string;
  items: OrderItem[];
}

export interface OrderItem {
  id: string;
  orderId: string;
  productId: string;
  productName: string;
  productSku: string;
  quantity: number;
  unitPrice: number;
  totalPrice: number;
}

export interface OrderStatusHistory {
  id: string;
  orderId: string;
  fromStatus?: OrderStatus;
  toStatus: OrderStatus;
  changedBy: string;
  changedByName: string;
  changedAt: string;
  notes?: string;
}

export interface CreateOrderRequest {
  customerId: string;
  notes?: string;
  items: CreateOrderItemRequest[];
}

export interface CreateOrderItemRequest {
  productId: string;
  quantity: number;
}

export interface UpdateOrderRequest {
  customerId?: string;
  notes?: string;
  items?: CreateOrderItemRequest[];
}

export interface OrderTransitionRequest {
  notes?: string;
}

export type OrderCreateRequest = CreateOrderRequest;
export type OrderUpdateRequest = UpdateOrderRequest;

export type OrderPage = Page<Order>;
