import { Component, OnInit, inject, DestroyRef, signal, computed } from '@angular/core';
import { RouterModule } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { InventoryService } from '../../../core/services/inventory.service';
import { ProductService } from '../../../core/services/product.service';
import { ToastService } from '../../../core/services/toast.service';
import { InventoryItem, Page, Product } from '../../../models';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize } from 'rxjs/operators';
import { StockReceiptDialogComponent } from '../stock-receipt-dialog/stock-receipt-dialog';
import { StockAdjustmentDialogComponent } from '../stock-adjustment-dialog/stock-adjustment-dialog';
import { StatusBadgeComponent } from '../../../shared/components/status-badge/status-badge';

@Component({
  selector: 'app-stock-levels',
  standalone: true,
  imports: [RouterModule, FormsModule, StockReceiptDialogComponent, StockAdjustmentDialogComponent, StatusBadgeComponent],
  templateUrl: './stock-levels.html',
  styleUrl: './stock-levels.scss'
})
export class StockLevelsComponent implements OnInit {
  private inventoryService = inject(InventoryService);
  private productService = inject(ProductService);
  private toastService = inject(ToastService);
  private destroyRef = inject(DestroyRef);

  inventoryItems = signal<InventoryItem[]>([]);
  products = signal(new Map<string, Product>());
  loading = signal(true);

  currentPage = signal(0);
  pageSize = 10;
  totalElements = signal(0);
  totalPages = signal(0);

  showLowStockOnly = signal(false);

  showReceiptModal = signal(false);
  showAdjustmentModal = signal(false);
  showReorderModal = signal(false);
  selectedInventory = signal<InventoryItem | null>(null);

  Math = Math;

  ngOnInit(): void {
    this.loadInventory();
  }

  loadInventory(): void {
    this.loading.set(true);

    const params: Record<string, string | number> = {
      page: this.currentPage(),
      size: this.pageSize,
      sort: 'product.name,asc'
    };

    this.inventoryService.getInventoryItems(params)
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.loading.set(false))
      )
      .subscribe({
        next: (page: Page<InventoryItem>) => {
          this.inventoryItems.set(page.content);
          this.totalElements.set(page.totalElements);
          this.totalPages.set(page.totalPages);
          this.currentPage.set(page.number);
          this.loadProducts();
        },
        error: (error) => {
          console.error('Failed to load inventory:', error);
          this.toastService.error('Failed to load inventory');
        }
      });
  }

  loadProducts(): void {
    const productIds = [...new Set(this.inventoryItems().map(item => item.productId))];
    productIds.forEach(productId => {
      this.productService.getProduct(productId)
        .pipe(takeUntilDestroyed(this.destroyRef))
        .subscribe({
          next: (product) => { this.products.update(m => new Map(m).set(product.id, product)); },
          error: (error) => { console.error(`Failed to load product ${productId}:`, error); }
        });
    });
  }

  toggleLowStockFilter(): void {
    this.showLowStockOnly.update(v => !v);
    if (this.showLowStockOnly()) { this.loadLowStockItems(); } else { this.loadInventory(); }
  }

  loadLowStockItems(): void {
    this.loading.set(true);
    this.inventoryService.getLowStockItems()
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.loading.set(false))
      )
      .subscribe({
        next: (items) => {
          this.inventoryItems.set(items);
          this.totalElements.set(items.length);
          this.totalPages.set(1);
          this.currentPage.set(0);
          this.loadProducts();
        },
        error: (error) => {
          console.error('Failed to load low stock items:', error);
          this.toastService.error('Failed to load low stock items');
        }
      });
  }

  goToPage(page: number): void {
    if (page >= 0 && page < this.totalPages() && !this.showLowStockOnly()) {
      this.currentPage.set(page);
      this.loadInventory();
    }
  }

  openReceiptModal(inventory: InventoryItem): void { this.selectedInventory.set(inventory); this.showReceiptModal.set(true); }
  openAdjustmentModal(inventory: InventoryItem): void { this.selectedInventory.set(inventory); this.showAdjustmentModal.set(true); }
  openReorderModal(inventory: InventoryItem): void { this.selectedInventory.set(inventory); this.showReorderModal.set(true); }

  closeModals(): void {
    this.showReceiptModal.set(false);
    this.showAdjustmentModal.set(false);
    this.showReorderModal.set(false);
    this.selectedInventory.set(null);
  }

  onModalSuccess(): void {
    this.closeModals();
    if (this.showLowStockOnly()) { this.loadLowStockItems(); } else { this.loadInventory(); }
  }

  getProduct(productId: string): Product | undefined { return this.products().get(productId); }
  getAvailableStock(item: InventoryItem): number { return item.quantityOnHand - item.quantityReserved; }
  isLowStock(item: InventoryItem): boolean { return this.getAvailableStock(item) <= item.reorderPoint; }

  stockLevel(item: InventoryItem): 'critical' | 'warning' | 'ok' {
    const available = this.getAvailableStock(item);
    if (available <= 0) return 'critical';
    if (this.isLowStock(item)) return 'warning';
    return 'ok';
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

