import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpContext, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Product, ProductCreateRequest, ProductUpdateRequest, ProductStatus, Page } from '../../models';

@Injectable({
  providedIn: 'root'
})
export class ProductService {
  private http = inject(HttpClient);
  private readonly API_URL = `${environment.apiUrl}/products`;

  getProducts(params?: {
    page?: number;
    size?: number;
    sort?: string;
    categoryId?: string;
    status?: ProductStatus;
    search?: string;
  }, context?: HttpContext): Observable<Page<Product>> {
    let httpParams = new HttpParams();
    
    if (params) {
      if (params.page !== undefined) httpParams = httpParams.set('page', params.page.toString());
      if (params.size !== undefined) httpParams = httpParams.set('size', params.size.toString());
      if (params.sort) httpParams = httpParams.set('sort', params.sort);
      if (params.categoryId) httpParams = httpParams.set('categoryId', params.categoryId);
      if (params.status) httpParams = httpParams.set('status', params.status);
      if (params.search) httpParams = httpParams.set('search', params.search);
    }
    
    return this.http.get<Page<Product>>(this.API_URL, { params: httpParams, context });
  }

  getProduct(id: string): Observable<Product> {
    return this.http.get<Product>(`${this.API_URL}/${id}`);
  }

  createProduct(request: ProductCreateRequest, context?: HttpContext): Observable<Product> {
    return this.http.post<Product>(this.API_URL, request, { context });
  }

  updateProduct(id: string, request: ProductUpdateRequest, context?: HttpContext): Observable<Product> {
    return this.http.put<Product>(`${this.API_URL}/${id}`, request, { context });
  }

  deleteProduct(id: string): Observable<void> {
    return this.http.delete<void>(`${this.API_URL}/${id}`);
  }
}
