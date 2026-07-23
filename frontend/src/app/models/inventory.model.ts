import { Page } from './common.model';

export enum MovementType {
  RECEIPT = 'RECEIPT',
  ADJUSTMENT = 'ADJUSTMENT',
  RESERVATION = 'RESERVATION',
  RESERVATION_RELEASE = 'RESERVATION_RELEASE',
  DEDUCTION = 'DEDUCTION'
}

export interface InventoryItem {
  id: string;
  tenantId: string;
  productId: string;
  productName: string;
  productSku: string;
  quantityOnHand: number;
  quantityReserved: number;
  availableQuantity: number;
  reorderPoint: number;
  lastUpdated: string;
}

export interface StockMovement {
  id: string;
  tenantId: string;
  productId: string;
  productName: string;
  productSku: string;
  type: MovementType;
  quantity: number;
  referenceId?: string;
  referenceType?: string;
  notes?: string;
  createdAt: string;
  createdBy: string;
  createdByName: string;
}

export interface ReceiveStockRequest {
  quantity: number;
  notes?: string;
}

export interface AdjustStockRequest {
  quantity: number;
  notes: string;
}

export interface UpdateReorderPointRequest {
  reorderPoint: number;
}

export type StockReceiptRequest = ReceiveStockRequest;
export type StockAdjustmentRequest = AdjustStockRequest;
export type ReorderPointRequest = UpdateReorderPointRequest;
export type StockMovementType = MovementType;

export type InventoryPage = Page<InventoryItem>;
export type StockMovementPage = Page<StockMovement>;
