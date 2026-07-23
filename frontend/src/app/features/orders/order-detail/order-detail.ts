import { Component, OnInit, inject, DestroyRef, signal, computed } from '@angular/core';
import { RouterModule, Router, ActivatedRoute } from '@angular/router';
import { DatePipe, CurrencyPipe } from '@angular/common';
import { OrderService } from '../../../core/services/order.service';
import { CustomerService } from '../../../core/services/customer.service';
import { ProductService } from '../../../core/services/product.service';
import { ToastService } from '../../../core/services/toast.service';
import { AuthService } from '../../../core/services/auth.service';
import { Order, OrderStatus, Customer, Product, OrderStatusHistory, UserRole } from '../../../models';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize } from 'rxjs/operators';
import { StatusHistoryComponent } from '../status-history/status-history';
import { StatusBadgeComponent } from '../../../shared/components/status-badge/status-badge';
import { ConfirmationDialog } from '../../../shared/components/confirmation-dialog/confirmation-dialog';

@Component({
  selector: 'app-order-detail',
  standalone: true,
  imports: [RouterModule, DatePipe, CurrencyPipe, StatusHistoryComponent, StatusBadgeComponent, ConfirmationDialog],
  templateUrl: './order-detail.html',
  styleUrl: './order-detail.scss'
})
export class OrderDetailComponent implements OnInit {
  private orderService = inject(OrderService);
  private customerService = inject(CustomerService);
  private productService = inject(ProductService);
  private toastService = inject(ToastService);
  private authService = inject(AuthService);
  private router = inject(Router);
  private route = inject(ActivatedRoute);
  private destroyRef = inject(DestroyRef);

  order = signal<Order | null>(null);
  customer = signal<Customer | null>(null);
  products = signal(new Map<string, Product>());
  statusHistory = signal<OrderStatusHistory[]>([]);
  loading = signal(true);
  actionInProgress = signal(false);

  pendingDelete = signal<Order | null>(null);

  OrderStatus = OrderStatus;

  canEdit         = computed(() => this.order()?.status === OrderStatus.DRAFT);
  canDelete       = computed(() => this.order()?.status === OrderStatus.DRAFT);
  canSubmit       = computed(() => this.order()?.status === OrderStatus.DRAFT);
  canApprove      = computed(() => this.order()?.status === OrderStatus.SUBMITTED && this.authService.hasAnyRole([UserRole.ADMIN, UserRole.MANAGER]));
  canReject       = computed(() => this.order()?.status === OrderStatus.SUBMITTED && this.authService.hasAnyRole([UserRole.ADMIN, UserRole.MANAGER]));
  canStartPicking = computed(() => this.order()?.status === OrderStatus.APPROVED);
  canShip         = computed(() => this.order()?.status === OrderStatus.PICKING);
  canDeliver      = computed(() => this.order()?.status === OrderStatus.SHIPPED);
  canCancel       = computed(() => {
    const status = this.order()?.status;
    return (status === OrderStatus.SUBMITTED || status === OrderStatus.APPROVED) &&
      this.authService.hasAnyRole([UserRole.ADMIN, UserRole.MANAGER]);
  });

  ngOnInit(): void {
    const orderId = this.route.snapshot.paramMap.get('id');
    if (orderId) {
      this.loadOrder(orderId);
    } else {
      this.router.navigate(['/orders']);
    }
  }

  loadOrder(id: string): void {
    this.loading.set(true);
    this.orderService.getOrder(id)
      .pipe(takeUntilDestroyed(this.destroyRef), finalize(() => this.loading.set(false)))
      .subscribe({
        next: (order) => {
          this.order.set(order);
          this.loadCustomer(order.customerId);
          this.loadProducts(order);
          this.loadStatusHistory(order.id);
        },
        error: (error) => {
          console.error('Failed to load order:', error);
          this.toastService.error('Failed to load order');
          this.router.navigate(['/orders']);
        }
      });
  }

  loadCustomer(customerId: string): void {
    this.customerService.getCustomer(customerId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (customer) => { this.customer.set(customer); },
        error: (error) => { console.error('Failed to load customer:', error); }
      });
  }

  loadProducts(order: Order): void {
    order.items.forEach(item => {
      if (this.products().has(item.productId)) return;
      this.productService.getProduct(item.productId)
        .pipe(takeUntilDestroyed(this.destroyRef))
        .subscribe({
          next: (product) => { this.products.update(m => new Map(m).set(product.id, product)); },
          error: (error) => { console.error(`Failed to load product ${item.productId}:`, error); }
        });
    });
  }

  loadStatusHistory(orderId: string): void {
    this.orderService.getOrderHistory(orderId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (history) => { this.statusHistory.set(history); },
        error: (error) => { console.error('Failed to load status history:', error); }
      });
  }

  performAction(action: string): void {
    const currentOrder = this.order();
    if (!currentOrder) return;

    this.actionInProgress.set(true);

    let operation;
    switch (action) {
      case 'submit':        operation = this.orderService.submitOrder(currentOrder.id); break;
      case 'approve':       operation = this.orderService.approveOrder(currentOrder.id); break;
      case 'reject':        operation = this.orderService.rejectOrder(currentOrder.id); break;
      case 'start-picking': operation = this.orderService.startPicking(currentOrder.id); break;
      case 'ship':          operation = this.orderService.shipOrder(currentOrder.id); break;
      case 'deliver':       operation = this.orderService.deliverOrder(currentOrder.id); break;
      case 'cancel':        operation = this.orderService.cancelOrder(currentOrder.id); break;
      default: this.actionInProgress.set(false); return;
    }

    operation
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (updatedOrder) => {
          this.order.set(updatedOrder);
          this.toastService.success(`Order ${action} successfully`);
          this.loadStatusHistory(updatedOrder.id);
          this.actionInProgress.set(false);
        },
        error: (error) => {
          console.error(`Failed to ${action} order:`, error);
          this.toastService.error(error.error?.message || `Failed to ${action} order`);
          this.actionInProgress.set(false);
        }
      });
  }

  requestDelete(): void {
    const currentOrder = this.order();
    if (currentOrder) this.pendingDelete.set(currentOrder);
  }

  confirmDelete(): void {
    const currentOrder = this.pendingDelete();
    if (!currentOrder) return;
    this.pendingDelete.set(null);

    this.orderService.deleteOrder(currentOrder.id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.toastService.success('Order deleted successfully');
          this.router.navigate(['/orders']);
        },
        error: (error) => {
          console.error('Failed to delete order:', error);
          this.toastService.error(error.error?.message || 'Failed to delete order');
        }
      });
  }

  cancelDelete(): void {
    this.pendingDelete.set(null);
  }

  getProduct(productId: string): Product | undefined {
    return this.products().get(productId);
  }
}

