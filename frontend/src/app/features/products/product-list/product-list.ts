import { Component, OnInit, inject, DestroyRef, signal, computed } from '@angular/core';
import { RouterModule } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { CurrencyPipe } from '@angular/common';
import { ProductService } from '../../../core/services/product.service';
import { CategoryService } from '../../../core/services/category.service';
import { ToastService } from '../../../core/services/toast.service';
import { Product, ProductStatus, Category, Page } from '../../../models';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize } from 'rxjs/operators';
import { StatusBadgeComponent } from '../../../shared/components/status-badge/status-badge';
import { ConfirmationDialog } from '../../../shared/components/confirmation-dialog/confirmation-dialog';

@Component({
  selector: 'app-product-list',
  standalone: true,
  imports: [RouterModule, FormsModule, CurrencyPipe, StatusBadgeComponent, ConfirmationDialog],
  templateUrl: './product-list.html',
  styleUrl: './product-list.scss'
})
export class ProductListComponent implements OnInit {
  private productService = inject(ProductService);
  private categoryService = inject(CategoryService);
  private toastService = inject(ToastService);
  private destroyRef = inject(DestroyRef);

  products = signal<Product[]>([]);
  categories = signal<Category[]>([]);
  loading = signal(true);

  currentPage = signal(0);
  pageSize = 10;
  totalElements = signal(0);
  totalPages = signal(0);

  searchTerm = '';
  selectedCategoryId = '';
  selectedStatus: ProductStatus | '' = '';

  pendingDeleteProduct = signal<Product | null>(null);

  ProductStatus = ProductStatus;
  Math = Math;

  ngOnInit(): void {
    this.loadCategories();
    this.loadProducts();
  }

  loadCategories(): void {
    this.categoryService.getCategories()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (categories) => { this.categories.set(categories); },
        error: (error) => { console.error('Failed to load categories:', error); }
      });
  }

  loadProducts(): void {
    this.loading.set(true);

    const params: {
      page: number; size: number; sort: string;
      search?: string; categoryId?: string; status?: ProductStatus;
    } = { page: this.currentPage(), size: this.pageSize, sort: 'name,asc' };

    if (this.searchTerm) params.search = this.searchTerm;
    if (this.selectedCategoryId) params.categoryId = this.selectedCategoryId;
    if (this.selectedStatus) params.status = this.selectedStatus;

    this.productService.getProducts(params)
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.loading.set(false))
      )
      .subscribe({
        next: (page: Page<Product>) => {
          this.products.set(page.content);
          this.totalElements.set(page.totalElements);
          this.totalPages.set(page.totalPages);
          this.currentPage.set(page.number);
        },
        error: (error) => {
          console.error('Failed to load products:', error);
          this.toastService.error('Failed to load products');
        }
      });
  }

  onSearch(): void { this.currentPage.set(0); this.loadProducts(); }
  onFilterChange(): void { this.currentPage.set(0); this.loadProducts(); }

  clearFilters(): void {
    this.searchTerm = '';
    this.selectedCategoryId = '';
    this.selectedStatus = '';
    this.currentPage.set(0);
    this.loadProducts();
  }

  goToPage(page: number): void {
    if (page >= 0 && page < this.totalPages()) { this.currentPage.set(page); this.loadProducts(); }
  }

  requestDelete(product: Product): void {
    this.pendingDeleteProduct.set(product);
  }

  confirmDelete(): void {
    const product = this.pendingDeleteProduct();
    if (!product) return;
    this.pendingDeleteProduct.set(null);

    this.productService.deleteProduct(product.id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => { this.toastService.success('Product deleted successfully'); this.loadProducts(); },
        error: (error) => { console.error('Failed to delete product:', error); this.toastService.error('Failed to delete product'); }
      });
  }

  cancelDelete(): void {
    this.pendingDeleteProduct.set(null);
  }

  getCategoryName(categoryId: string | null): string {
    if (!categoryId) return 'N/A';
    return this.categories().find(c => c.id === categoryId)?.name ?? 'Unknown';
  }

  pageNumbers = computed<number[]>(() => {
    const pages: number[] = [];
    const maxPages = 5;
    const currentPage = this.currentPage();
    const totalPages = this.totalPages();
    let startPage = Math.max(0, currentPage - Math.floor(maxPages / 2));
    const endPage = Math.min(totalPages - 1, startPage + maxPages - 1);
    if (endPage - startPage < maxPages - 1) startPage = Math.max(0, endPage - maxPages + 1);
    for (let i = startPage; i <= endPage; i++) pages.push(i);
    return pages;
  });
}

