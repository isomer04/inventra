import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpContext, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Customer, CustomerCreateRequest, CustomerUpdateRequest, CustomerStatus, Page } from '../../models';

@Injectable({
  providedIn: 'root'
})
export class CustomerService {
  private http = inject(HttpClient);
  private readonly API_URL = `${environment.apiUrl}/customers`;

  getCustomers(params?: {
    page?: number;
    size?: number;
    sort?: string;
    search?: string;
    status?: CustomerStatus;
  }, context?: HttpContext): Observable<Page<Customer>> {
    let httpParams = new HttpParams();
    
    if (params) {
      if (params.page !== undefined) httpParams = httpParams.set('page', params.page.toString());
      if (params.size !== undefined) httpParams = httpParams.set('size', params.size.toString());
      if (params.sort) httpParams = httpParams.set('sort', params.sort);
      if (params.search) httpParams = httpParams.set('search', params.search);
      if (params.status) httpParams = httpParams.set('status', params.status);
    }
    
    return this.http.get<Page<Customer>>(this.API_URL, { params: httpParams, context });
  }

  getCustomer(id: string): Observable<Customer> {
    return this.http.get<Customer>(`${this.API_URL}/${id}`);
  }

  createCustomer(request: CustomerCreateRequest): Observable<Customer> {
    return this.http.post<Customer>(this.API_URL, request);
  }

  updateCustomer(id: string, request: CustomerUpdateRequest): Observable<Customer> {
    return this.http.put<Customer>(`${this.API_URL}/${id}`, request);
  }

  deleteCustomer(id: string): Observable<void> {
    return this.http.delete<void>(`${this.API_URL}/${id}`);
  }
}
