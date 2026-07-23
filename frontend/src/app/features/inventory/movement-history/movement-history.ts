import { Component, OnInit, inject, DestroyRef, signal, computed } from '@angular/core';
import { RouterModule } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { DatePipe } from '@angular/common';
import { StockMovementService } from '../../../core/services/stock-movement.service';
import { ProductService } from '../../../core/services/product.service';
import { ToastService } from '../../../core/services/toast.service';
import { StockMovement, MovementType, Page, Product } from '../../../models';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize } from 'rxjs/operators';
import { StatusBadgeComponent } from '../../../shared/components/status-badge/status-badge';

@Component({
  selector: 'app-movement-history',
  standalone: true,
  imports: [RouterModule, FormsModule, DatePipe, StatusBadgeComponent],
  templateUrl: './movement-history.html',
  styleUrl: './movement-history.scss'
})
export class MovementHistoryComponent implements OnInit {
  private movementService = inject(StockMovementService);
  private productService = inject(ProductService);
  private toastService = inject(ToastService);
  private destroyRef = inject(DestroyRef);

  movements = signal<StockMovement[]>([]);
  products = signal(new Map<string, Product>());
  loading = signal(true);

  currentPage = signal(0);
  pageSize = 20;
  totalElements = signal(0);
  totalPages = signal(0);

  selectedType: MovementType | '' = '';
  startDate = '';
  endDate = '';

  MovementType = MovementType;
  Math = Math;

  ngOnInit(): void {
    this.loadMovements();
  }

  loadMovements(): void {
    this.loading.set(true);

    const params: Record<string, string | number> = { page: this.currentPage(), size: this.pageSize, sort: 'createdAt,desc' };
    if (this.selectedType) params['type'] = this.selectedType;
    if (this.startDate) params['startDate'] = this.startDate;
    if (this.endDate) params['endDate'] = this.endDate;

    this.movementService.getMovements(params)
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.loading.set(false))
      )
      .subscribe({
        next: (page: Page<StockMovement>) => {
          this.movements.set(page.content);
          this.totalElements.set(page.totalElements);
          this.totalPages.set(page.totalPages);
          this.currentPage.set(page.number);
          this.loadProducts();
        },
        error: (error) => {
          console.error('Failed to load movements:', error);
          this.toastService.error('Failed to load movements');
        }
      });
  }

  loadProducts(): void {
    const productIds = [...new Set(this.movements().map(m => m.productId))];
    productIds.forEach(productId => {
      if (!this.products().has(productId)) {
        this.productService.getProduct(productId)
          .pipe(takeUntilDestroyed(this.destroyRef))
          .subscribe({
            next: (product) => { this.products.update(m => new Map(m).set(product.id, product)); },
            error: (error) => { console.error(`Failed to load product ${productId}:`, error); }
          });
      }
    });
  }

  onFilterChange(): void { this.currentPage.set(0); this.loadMovements(); }

  clearFilters(): void {
    this.selectedType = '';
    this.startDate = '';
    this.endDate = '';
    this.currentPage.set(0);
    this.loadMovements();
  }

  goToPage(page: number): void {
    if (page >= 0 && page < this.totalPages()) { this.currentPage.set(page); this.loadMovements(); }
  }

  getProduct(productId: string): Product | undefined { return this.products().get(productId); }

  movementTypeToStatus(type: MovementType): string {
    switch (type) {
      case MovementType.RECEIPT:             return 'delivered';
      case MovementType.ADJUSTMENT:          return 'processing';
      case MovementType.RESERVATION:         return 'shipped';
      case MovementType.RESERVATION_RELEASE: return 'pending';
      case MovementType.DEDUCTION:           return 'cancelled';
      default:                               return 'pending';
    }
  }

  getQuantityClass(quantity: number): string { return quantity > 0 ? 'qty-positive' : 'qty-negative'; }

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

