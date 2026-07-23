import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { of, throwError } from 'rxjs';
import { ProductListComponent } from './product-list';
import { ProductService } from '../../../core/services/product.service';
import { ToastService } from '../../../core/services/toast.service';
import { Product, ProductStatus, Page } from '../../../models';
import type { MockedObject } from 'vitest';

describe('ProductListComponent', () => {
  let component: ProductListComponent;
  let fixture: ComponentFixture<ProductListComponent>;
  let productService: MockedObject<ProductService>;
  let toastService: MockedObject<ToastService>;

  const mockProducts: Product[] = [
    { id: 'p1', sku: 'SKU-001', name: 'Product 1', unitPrice: 10, status: ProductStatus.ACTIVE, tenantId: 't1', createdAt: '2026-01-01', updatedAt: '2026-01-01' },
    { id: 'p2', sku: 'SKU-002', name: 'Product 2', unitPrice: 20, status: ProductStatus.ACTIVE, tenantId: 't1', createdAt: '2026-01-02', updatedAt: '2026-01-02' }
  ];

  const mockPage: Page<Product> = {
    content: mockProducts,
    number: 0, size: 10, totalElements: 2, totalPages: 1
  };

  beforeEach(async () => {
    const productSpy = { getProducts: vi.fn(), deleteProduct: vi.fn() };
    const toastSpy = { success: vi.fn(), error: vi.fn() };

    await TestBed.configureTestingModule({
      imports: [ProductListComponent, FormsModule],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: ProductService, useValue: productSpy },
        { provide: ToastService, useValue: toastSpy }
      ]
    }).compileComponents();

    productService = TestBed.inject(ProductService) as unknown as MockedObject<ProductService>;
    toastService = TestBed.inject(ToastService) as unknown as MockedObject<ToastService>;
    productService.getProducts.mockReturnValue(of(mockPage));

    fixture = TestBed.createComponent(ProductListComponent);
    component = fixture.componentInstance;
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should load products on init', () => {
    fixture.detectChanges();
    expect(productService.getProducts).toHaveBeenCalled();
    expect(component.products().length).toBe(2);
  });

  it('should filter by search term', () => {
    fixture.detectChanges();
    component.searchTerm = 'Product 1';
    component.onSearch();
    expect(productService.getProducts).toHaveBeenCalledWith(expect.objectContaining({ search: 'Product 1' }));
  });

  it('should filter by status', () => {
    fixture.detectChanges();
    component.selectedStatus = ProductStatus.ACTIVE;
    component.onFilterChange();
    expect(productService.getProducts).toHaveBeenCalledWith(expect.objectContaining({ status: ProductStatus.ACTIVE }));
  });

  it('should navigate to page', () => {
    // goToPage() reloads, and the component re-syncs currentPage from the
    // response, so the mocked page must report the page that was requested.
    const multiPage: Page<Product> = { ...mockPage, totalPages: 5, number: 2 };
    productService.getProducts.mockReturnValue(of(multiPage));
    fixture.detectChanges();

    component.goToPage(2);
    expect(component.currentPage()).toBe(2);
  });

  it('should delete product on confirmation', () => {
    productService.deleteProduct.mockReturnValue(of(void 0));
    fixture.detectChanges();
    component.pendingDeleteProduct.set(mockProducts[0]);
    
    component.confirmDelete();
    expect(productService.deleteProduct).toHaveBeenCalledWith('p1');
    expect(toastService.success).toHaveBeenCalledWith('Product deleted successfully');
  });

  it('should show error on delete failure', () => {
    productService.deleteProduct.mockReturnValue(throwError(() => ({ error: { message: 'Delete failed' } })));
    fixture.detectChanges();
    component.pendingDeleteProduct.set(mockProducts[0]);
    
    component.confirmDelete();
    // The component reports a fixed message rather than echoing the server's.
    expect(toastService.error).toHaveBeenCalledWith('Failed to delete product');
  });

  it('should cancel deletion', () => {
    fixture.detectChanges();
    component.pendingDeleteProduct.set(mockProducts[0]);
    component.cancelDelete();
    expect(component.pendingDeleteProduct()).toBeNull();
  });

  it('should clear filters', () => {
    fixture.detectChanges();
    component.searchTerm = 'test';
    component.selectedStatus = ProductStatus.ACTIVE;
    component.clearFilters();
    expect(component.searchTerm).toBe('');
    expect(component.selectedStatus).toBe('');
  });

  it('should handle empty results', () => {
    const emptyPage: Page<Product> = { content: [], number: 0, size: 10, totalElements: 0, totalPages: 0 };
    productService.getProducts.mockReturnValue(of(emptyPage));
    fixture.detectChanges();
    expect(component.products().length).toBe(0);
  });

  it('should handle load error', () => {
    productService.getProducts.mockReturnValue(throwError(() => ({ status: 500 })));
    fixture.detectChanges();
    expect(toastService.error).toHaveBeenCalledWith('Failed to load products');
  });
});
