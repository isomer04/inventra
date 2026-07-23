import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpContext, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Order, OrderCreateRequest, OrderUpdateRequest, OrderStatus, OrderStatusHistory, Page, OrderTransitionRequest } from '../../models';

@Injectable({
  providedIn: 'root'
})
export class OrderService {
  private http = inject(HttpClient);
  private readonly API_URL = `${environment.apiUrl}/orders`;

  getOrders(params?: {
    page?: number;
    size?: number;
    sort?: string;
    status?: OrderStatus;
    customerId?: string;
    startDate?: string;
    endDate?: string;
  }, context?: HttpContext): Observable<Page<Order>> {
    let httpParams = new HttpParams();
    
    if (params) {
      if (params.page !== undefined) httpParams = httpParams.set('page', params.page.toString());
      if (params.size !== undefined) httpParams = httpParams.set('size', params.size.toString());
      if (params.sort) httpParams = httpParams.set('sort', params.sort);
      if (params.status) httpParams = httpParams.set('status', params.status);
      if (params.customerId) httpParams = httpParams.set('customerId', params.customerId);
      if (params.startDate) httpParams = httpParams.set('startDate', params.startDate);
      if (params.endDate) httpParams = httpParams.set('endDate', params.endDate);
    }
    
    return this.http.get<Page<Order>>(this.API_URL, { params: httpParams, context });
  }

  getOrder(id: string): Observable<Order> {
    return this.http.get<Order>(`${this.API_URL}/${id}`);
  }

  createOrder(request: OrderCreateRequest): Observable<Order> {
    return this.http.post<Order>(this.API_URL, request);
  }

  updateOrder(id: string, request: OrderUpdateRequest): Observable<Order> {
    return this.http.put<Order>(`${this.API_URL}/${id}`, request);
  }

  deleteOrder(id: string): Observable<void> {
    return this.http.delete<void>(`${this.API_URL}/${id}`);
  }

  submitOrder(id: string, request?: OrderTransitionRequest): Observable<Order> {
    return this.http.post<Order>(`${this.API_URL}/${id}/submit`, request || {});
  }

  approveOrder(id: string, request?: OrderTransitionRequest): Observable<Order> {
    return this.http.post<Order>(`${this.API_URL}/${id}/approve`, request || {});
  }

  rejectOrder(id: string, request?: OrderTransitionRequest): Observable<Order> {
    return this.http.post<Order>(`${this.API_URL}/${id}/reject`, request || {});
  }

  startPicking(id: string, request?: OrderTransitionRequest): Observable<Order> {
    return this.http.post<Order>(`${this.API_URL}/${id}/start-picking`, request || {});
  }

  shipOrder(id: string, request?: OrderTransitionRequest): Observable<Order> {
    return this.http.post<Order>(`${this.API_URL}/${id}/ship`, request || {});
  }

  deliverOrder(id: string, request?: OrderTransitionRequest): Observable<Order> {
    return this.http.post<Order>(`${this.API_URL}/${id}/deliver`, request || {});
  }

  cancelOrder(id: string, request?: OrderTransitionRequest): Observable<Order> {
    return this.http.post<Order>(`${this.API_URL}/${id}/cancel`, request || {});
  }

  getOrderHistory(id: string): Observable<OrderStatusHistory[]> {
    return this.http.get<OrderStatusHistory[]>(`${this.API_URL}/${id}/history`);
  }
}
