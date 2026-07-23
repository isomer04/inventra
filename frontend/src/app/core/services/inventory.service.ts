import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpContext, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { InventoryItem, StockReceiptRequest, StockAdjustmentRequest, ReorderPointRequest, Page } from '../../models';

@Injectable({
  providedIn: 'root'
})
export class InventoryService {
  private http = inject(HttpClient);
  private readonly API_URL = `${environment.apiUrl}/inventory`;

  getInventoryItems(params?: {
    page?: number;
    size?: number;
    sort?: string;
  }, context?: HttpContext): Observable<Page<InventoryItem>> {
    let httpParams = new HttpParams();
    
    if (params) {
      if (params.page !== undefined) httpParams = httpParams.set('page', params.page.toString());
      if (params.size !== undefined) httpParams = httpParams.set('size', params.size.toString());
      if (params.sort) httpParams = httpParams.set('sort', params.sort);
    }
    
    return this.http.get<Page<InventoryItem>>(this.API_URL, { params: httpParams, context });
  }

  getInventoryByProduct(productId: string): Observable<InventoryItem> {
    return this.http.get<InventoryItem>(`${this.API_URL}/${productId}`);
  }

  getLowStockItems(context?: HttpContext): Observable<InventoryItem[]> {
    return this.http.get<InventoryItem[]>(`${this.API_URL}/low-stock`, { context });
  }

  receiveStock(productId: string, request: StockReceiptRequest): Observable<InventoryItem> {
    return this.http.put<InventoryItem>(`${this.API_URL}/${productId}/receive`, request);
  }

  adjustStock(productId: string, request: StockAdjustmentRequest): Observable<InventoryItem> {
    return this.http.put<InventoryItem>(`${this.API_URL}/${productId}/adjust`, request);
  }

  updateReorderPoint(productId: string, request: ReorderPointRequest): Observable<InventoryItem> {
    return this.http.put<InventoryItem>(`${this.API_URL}/${productId}/reorder-point`, request);
  }
}
