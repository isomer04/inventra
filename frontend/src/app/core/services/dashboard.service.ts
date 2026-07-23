import { Injectable, inject } from '@angular/core';
import { HttpContext } from '@angular/common/http';
import { forkJoin, Observable, of } from 'rxjs';
import { catchError, map } from 'rxjs/operators';

import { ReportService } from './report.service';
import { InventoryService } from './inventory.service';
import { OrderService } from './order.service';
import { ProductService } from './product.service';
import { CustomerService } from './customer.service';
import { DashboardOverview } from '../../models/dashboard.model';
import { OrderStatus } from '../../models/order.model';
import {
  InventorySummary,
  OrderSummaryReport,
  TopProductsReport,
  StockMovementReport,
} from '../../models/report.model';
import { InventoryItem } from '../../models/inventory.model';
import { Order, Page } from '../../models';
import { SILENT_ERROR } from '../interceptors/http-context-tokens';

/** Format a Date as YYYY-MM-DD without pulling in date-fns. */
function isoDate(d: Date): string {
  return d.toISOString().slice(0, 10);
}

/**
 * Shared HttpContext that suppresses the global error-interceptor toast.
 * Dashboard calls handle their own errors via catchError — showing a toast
 * on top of a graceful empty-widget fallback would be confusing.
 */
const silentCtx = new HttpContext().set(SILENT_ERROR, true);

@Injectable({
  providedIn: 'root',
})
export class DashboardService {
  private reportService = inject(ReportService);
  private inventoryService = inject(InventoryService);
  private orderService = inject(OrderService);
  private productService = inject(ProductService);
  private customerService = inject(CustomerService);

  /**
   * Fires all dashboard API calls in parallel and merges them into a
   * single DashboardOverview. Each call is individually guarded with
   * catchError so one failure doesn't kill the whole dashboard.
   */
  loadOverview(): Observable<DashboardOverview> {
    const today = new Date();
    const thirtyDaysAgo = new Date(today);
    thirtyDaysAgo.setDate(today.getDate() - 30);
    const startDate = isoDate(thirtyDaysAgo);
    const endDate = isoDate(today);

    const inventorySummary$ = this.reportService
      .getInventorySummary()
      .pipe(catchError(() => of(this.emptyInventorySummary())));

    const orderSummary$ = this.reportService
      .getOrderSummary({ startDate, endDate })
      .pipe(catchError(() => of(this.emptyOrderSummary(startDate, endDate))));

    const topProducts$ = this.reportService
      .getTopProducts({ days: 30, limit: 5 })
      .pipe(catchError(() => of(this.emptyTopProducts())));

    // groupBy=date_type returns rows with both date + type so the stacked
    // bar chart can correctly split each day by movement type.
    const stockMovements$ = this.reportService
      .getStockMovements({ startDate, endDate, groupBy: 'date_type' })
      .pipe(catchError(() => of(this.emptyStockMovements(startDate, endDate))));

    const lowStock$ = this.inventoryService
      .getLowStockItems(silentCtx)
      .pipe(catchError(() => of([] as InventoryItem[])));

    const recentOrders$ = this.orderService
      .getOrders({ size: 5, sort: 'createdAt,desc' }, silentCtx)
      .pipe(
        map((page: Page<Order>) => page.content),
        catchError(() => of([] as Order[])),
      );

    const pendingOrders$ = this.orderService
      .getOrders({ status: OrderStatus.SUBMITTED, size: 1 }, silentCtx)
      .pipe(
        map((page: Page<Order>) => page.totalElements),
        catchError(() => of(0)),
      );

    // Dedicated calls for alert counts — not limited to the 5 recent orders.
    const awaitingApprovalOrders$ = this.orderService
      .getOrders({ status: OrderStatus.SUBMITTED, size: 5, sort: 'createdAt,desc' }, silentCtx)
      .pipe(
        map((page: Page<Order>) => page.content),
        catchError(() => of([] as Order[])),
      );

    const readyToShipOrders$ = this.orderService
      .getOrders({ status: OrderStatus.APPROVED, size: 5, sort: 'createdAt,desc' }, silentCtx)
      .pipe(
        map((page: Page<Order>) => page.content),
        catchError(() => of([] as Order[])),
      );

    const totalProducts$ = this.productService
      .getProducts({ size: 1 }, silentCtx)
      .pipe(
        map((page) => page.totalElements),
        catchError(() => of(0)),
      );

    const totalCustomers$ = this.customerService
      .getCustomers({ size: 1 }, silentCtx)
      .pipe(
        map((page) => page.totalElements),
        catchError(() => of(0)),
      );

    return forkJoin({
      inventorySummary: inventorySummary$,
      orderSummary: orderSummary$,
      topProducts: topProducts$,
      stockMovements: stockMovements$,
      lowStockItems: lowStock$,
      recentOrders: recentOrders$,
      pendingOrderCount: pendingOrders$,
      awaitingApprovalOrders: awaitingApprovalOrders$,
      readyToShipOrders: readyToShipOrders$,
      totalProducts: totalProducts$,
      totalCustomers: totalCustomers$,
    }).pipe(
      map((results) => ({
        ...results,
        loadedAt: new Date(),
      })),
    );
  }

  private emptyInventorySummary(): InventorySummary {
    return {
      totalSkus: 0,
      totalStockValue: 0,
      lowStockCount: 0,
      totalQuantityOnHand: 0,
      totalQuantityReserved: 0,
      totalQuantityAvailable: 0,
      generatedAt: new Date().toISOString(),
    };
  }

  private emptyOrderSummary(startDate: string, endDate: string): OrderSummaryReport {
    return {
      startDate,
      endDate,
      ordersByStatus: [],
      totalOrders: 0,
      totalGmv: 0,
      generatedAt: new Date().toISOString(),
    };
  }

  private emptyTopProducts(): TopProductsReport {
    return {
      days: 30,
      limit: 5,
      startDate: '',
      endDate: '',
      products: [],
      generatedAt: new Date().toISOString(),
    };
  }

  private emptyStockMovements(startDate: string, endDate: string): StockMovementReport {
    return {
      groupBy: 'date_type',
      startDate,
      endDate,
      movements: [],
      generatedAt: new Date().toISOString(),
    };
  }
}
