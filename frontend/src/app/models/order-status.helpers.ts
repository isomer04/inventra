import { OrderStatus } from './order.model';

export interface StatusConfig {
  label: string;
  badgeClass: string;
  /** Bootstrap 5 hex colour used in Chart.js datasets */
  color: string;
}

/** Single source of truth for every status presentation. */
export const ORDER_STATUS_CONFIG: Record<OrderStatus, StatusConfig> = {
  [OrderStatus.DRAFT]:      { label: 'Draft',      badgeClass: 'bg-secondary',          color: '#adb5bd' },
  [OrderStatus.SUBMITTED]:  { label: 'Submitted',  badgeClass: 'bg-primary',             color: '#0d6efd' },
  [OrderStatus.APPROVED]:   { label: 'Approved',   badgeClass: 'bg-info text-dark',      color: '#6610f2' },
  [OrderStatus.PICKING]:    { label: 'Picking',    badgeClass: 'bg-warning text-dark',   color: '#fd7e14' },
  [OrderStatus.SHIPPED]:    { label: 'Shipped',    badgeClass: 'bg-info text-dark',      color: '#0dcaf0' },
  [OrderStatus.DELIVERED]:  { label: 'Delivered',  badgeClass: 'bg-success',             color: '#198754' },
  [OrderStatus.REJECTED]:   { label: 'Rejected',   badgeClass: 'bg-danger',              color: '#dc3545' },
  [OrderStatus.CANCELLED]:  { label: 'Cancelled',  badgeClass: 'bg-secondary',           color: '#6c757d' },
};

export function statusConfig(status: string): StatusConfig {
  return ORDER_STATUS_CONFIG[status as OrderStatus] ?? { label: status, badgeClass: 'bg-secondary', color: '#6c757d' };
}
