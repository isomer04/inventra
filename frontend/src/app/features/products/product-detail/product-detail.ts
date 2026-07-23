import { Component, OnInit, inject, DestroyRef, signal, computed } from '@angular/core';
import { RouterModule, Router, ActivatedRoute } from '@angular/router';
import { DatePipe, CurrencyPipe } from '@angular/common';
import { ProductService } from '../../../core/services/product.service';
import { CategoryService } from '../../../core/services/category.service';
import { InventoryService } from '../../../core/services/inventory.service';
import { ToastService } from '../../../core/services/toast.service';
import { Product, ProductStatus, Category, InventoryItem } from '../../../models';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { switchMap, finalize } from 'rxjs/operators';
import { of } from 'rxjs';
import { StatusBadgeComponent } from '../../../shared/components/status-badge/status-badge';
import { ConfirmationDialog } from '../../../shared/components/confirmation-dialog/confirmation-dialog';

@Component({
  selector: 'app-product-detail',
  standalone: true,
  imports: [RouterModule, DatePipe, CurrencyPipe, StatusBadgeComponent, ConfirmationDialog],
  templateUrl: './product-detail.html',
  styleUrl: './product-detail.scss'
})
export class ProductDetailComponent implements OnInit {
  private productService = inject(ProductService);
  private categoryService = inject(CategoryService);
  private inventoryService = inject(InventoryService);
  private toastService = inject(ToastService);
  private router = inject(Router);
  private route = inject(ActivatedRoute);
  private destroyRef = inject(DestroyRef);

  product = signal<Product | null>(null);
  category = signal<Category | null>(null);
  inventory = signal<InventoryItem | null>(null);
  loading = signal(true);

  pendingDelete = signal<Product | null>(null);

  ProductStatus = ProductStatus;

  ngOnInit(): void {
    this.route.paramMap
      .pipe(
        switchMap(params => {
          const productId = params.get('id');
          this.loading.set(true);

          if (!productId) {
            this.router.navigate(['/products']);
            return of(null).pipe(finalize(() => this.loading.set(false)));
          }

          this.product.set(null);
          this.category.set(null);
          this.inventory.set(null);
          return this.productService.getProduct(productId)
            .pipe(finalize(() => this.loading.set(false)));
        }),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe({
        next: (product) => {
          if (product) { this.product.set(product); this.loadAdditionalData(product); }
        },
        error: (error) => {
          console.error('Failed to load product:', error);
          this.toastService.error('Failed to load product');
          this.router.navigate(['/products']);
        }
      });
  }

  loadAdditionalData(product: Product): void {
    this.category.set(null);
    this.inventory.set(null);

    if (product.categoryId) {
      this.categoryService.getCategory(product.categoryId)
        .pipe(takeUntilDestroyed(this.destroyRef))
        .subscribe({
          next: (category) => { this.category.set(category); },
          error: (error) => { console.error('Failed to load category:', error); }
        });
    }

    this.inventoryService.getInventoryByProduct(product.id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (inventory) => { this.inventory.set(inventory); },
        error: (error) => { console.error('Failed to load inventory:', error); }
      });
  }

  requestDelete(): void {
    const product = this.product();
    if (product) this.pendingDelete.set(product);
  }

  confirmDelete(): void {
    const product = this.pendingDelete();
    if (!product) return;
    this.pendingDelete.set(null);

    this.productService.deleteProduct(product.id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => { this.toastService.success('Product deleted successfully'); this.router.navigate(['/products']); },
        error: (error) => {
          console.error('Failed to delete product:', error);
          this.toastService.error(error.error?.message || 'Failed to delete product');
        }
      });
  }

  cancelDelete(): void {
    this.pendingDelete.set(null);
  }

  availableStock = computed<number>(() => {
    const inventory = this.inventory();
    if (!inventory) return 0;
    return inventory.quantityOnHand - inventory.quantityReserved;
  });

  isLowStock = computed<boolean>(() => {
    const inventory = this.inventory();
    if (!inventory) return false;
    return this.availableStock() <= inventory.reorderPoint;
  });
}

