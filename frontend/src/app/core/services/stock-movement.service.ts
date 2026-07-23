import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { StockMovement, StockMovementType, Page } from '../../models';

@Injectable({
  providedIn: 'root'
})
export class StockMovementService {
  private http = inject(HttpClient);
  private readonly API_URL = `${environment.apiUrl}/inventory/movements`;

  getMovements(params?: {
    page?: number;
    size?: number;
    sort?: string;
    productId?: string;
    type?: StockMovementType;
    startDate?: string;
    endDate?: string;
  }): Observable<Page<StockMovement>> {
    let httpParams = new HttpParams();
    
    if (params) {
      if (params.page !== undefined) httpParams = httpParams.set('page', params.page.toString());
      if (params.size !== undefined) httpParams = httpParams.set('size', params.size.toString());
      if (params.sort) httpParams = httpParams.set('sort', params.sort);
      if (params.productId) httpParams = httpParams.set('productId', params.productId);
      if (params.type) httpParams = httpParams.set('type', params.type);
      if (params.startDate) httpParams = httpParams.set('startDate', params.startDate);
      if (params.endDate) httpParams = httpParams.set('endDate', params.endDate);
    }
    
    return this.http.get<Page<StockMovement>>(this.API_URL, { params: httpParams });
  }

  getMovementsByProduct(productId: string): Observable<StockMovement[]> {
    return this.http.get<StockMovement[]>(`${this.API_URL}/${productId}`);
  }
}
