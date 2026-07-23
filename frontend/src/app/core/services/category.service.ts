import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Category, CategoryCreateRequest, CategoryUpdateRequest } from '../../models';

@Injectable({
  providedIn: 'root'
})
export class CategoryService {
  private http = inject(HttpClient);
  private readonly API_URL = `${environment.apiUrl}/categories`;

  getCategories(): Observable<Category[]> {
    return this.http.get<Category[]>(this.API_URL);
  }

  getCategory(id: string): Observable<Category> {
    return this.http.get<Category>(`${this.API_URL}/${id}`);
  }

  createCategory(request: CategoryCreateRequest): Observable<Category> {
    return this.http.post<Category>(this.API_URL, request);
  }

  updateCategory(id: string, request: CategoryUpdateRequest): Observable<Category> {
    return this.http.put<Category>(`${this.API_URL}/${id}`, request);
  }

  deleteCategory(id: string): Observable<void> {
    return this.http.delete<void>(`${this.API_URL}/${id}`);
  }
}
