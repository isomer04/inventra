import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpContext, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  InventorySummary,
  StockMovementReport,
  OrderSummaryReport,
  TopProductsReport,
} from '../../models';
import { SILENT_ERROR } from '../interceptors/http-context-tokens';

/** Shared context that suppresses the global error-interceptor toast.
 *  Used for all report calls so the dashboard can handle errors itself. */
const silentContext = new HttpContext().set(SILENT_ERROR, true);

@Injectable({
  providedIn: 'root',
})
export class ReportService {
  private http = inject(HttpClient);
  private readonly API_URL = `${environment.apiUrl}/reports`;

  getInventorySummary(): Observable<InventorySummary> {
    return this.http.get<InventorySummary>(`${this.API_URL}/inventory-summary`, {
      context: silentContext,
    });
  }

  getStockMovements(params?: {
    startDate?: string;
    endDate?: string;
    groupBy?: 'type' | 'date' | 'date_type';
  }): Observable<StockMovementReport> {
    let httpParams = new HttpParams();
    if (params) {
      if (params.startDate) httpParams = httpParams.set('startDate', params.startDate);
      if (params.endDate) httpParams = httpParams.set('endDate', params.endDate);
      if (params.groupBy) httpParams = httpParams.set('groupBy', params.groupBy);
    }
    return this.http.get<StockMovementReport>(`${this.API_URL}/stock-movements`, {
      params: httpParams,
      context: silentContext,
    });
  }

  getOrderSummary(params?: {
    startDate?: string;
    endDate?: string;
  }): Observable<OrderSummaryReport> {
    let httpParams = new HttpParams();
    if (params) {
      if (params.startDate) httpParams = httpParams.set('startDate', params.startDate);
      if (params.endDate) httpParams = httpParams.set('endDate', params.endDate);
    }
    return this.http.get<OrderSummaryReport>(`${this.API_URL}/order-summary`, {
      params: httpParams,
      context: silentContext,
    });
  }

  getTopProducts(params?: { days?: 30 | 60 | 90; limit?: number }): Observable<TopProductsReport> {
    let httpParams = new HttpParams();
    if (params) {
      if (params.days) httpParams = httpParams.set('days', params.days.toString());
      if (params.limit) httpParams = httpParams.set('limit', params.limit.toString());
    }
    return this.http.get<TopProductsReport>(`${this.API_URL}/top-products`, {
      params: httpParams,
      context: silentContext,
    });
  }
}
