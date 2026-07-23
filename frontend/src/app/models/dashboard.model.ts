import { InventorySummary, OrderSummaryReport, TopProductsReport, StockMovementReport } from './report.model';
import { InventoryItem } from './inventory.model';
import { Order } from './order.model';

/** Aggregated result of all parallel dashboard API calls */
export interface DashboardOverview {
  inventorySummary: InventorySummary;
  orderSummary: OrderSummaryReport;
  topProducts: TopProductsReport;
  stockMovements: StockMovementReport;
  lowStockItems: InventoryItem[];
  recentOrders: Order[];
  /** Top-5 SUBMITTED orders for the alerts panel (accurate, not derived from recentOrders). */
  awaitingApprovalOrders: Order[];
  /** Top-5 APPROVED orders for the alerts panel (accurate, not derived from recentOrders). */
  readyToShipOrders: Order[];
  totalProducts: number;
  totalCustomers: number;
  pendingOrderCount: number;
  loadedAt: Date;
}
