export interface InventorySummary {
  totalSkus: number;
  totalStockValue: number;
  lowStockCount: number;
  totalQuantityOnHand: number;
  totalQuantityReserved: number;
  totalQuantityAvailable: number;
  generatedAt: string;
}

export interface MovementGroup {
  /** Present when groupBy=type or groupBy=date_type */
  type?: string;
  /** Present when groupBy=date or groupBy=date_type (ISO date string) */
  date?: string;
  count: number;
  totalQuantity: number;
}

export interface StockMovementReport {
  groupBy: string;
  startDate: string;
  endDate: string;
  movements: MovementGroup[];
  generatedAt: string;
}

export interface OrderStatusGroup {
  /** Typed as OrderStatus enum value */
  status: string;
  count: number;
  totalAmount: number;
}

export interface OrderSummaryReport {
  startDate: string;
  endDate: string;
  ordersByStatus: OrderStatusGroup[];
  totalOrders: number;
  totalGmv: number;
  averageFulfillmentTimeHours?: number;
  generatedAt: string;
}

export interface TopProduct {
  productId: string;
  sku: string;
  name: string;
  orderCount: number;
  totalQuantitySold: number;
  totalRevenue: number;
}

export interface TopProductsReport {
  days: number;
  limit: number;
  startDate: string;
  endDate: string;
  products: TopProduct[];
  generatedAt: string;
}
