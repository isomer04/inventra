import { Component, OnInit, inject, DestroyRef, signal, computed } from '@angular/core';
import { RouterModule } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { CurrencyPipe, DatePipe, KeyValuePipe } from '@angular/common';
import { OrderService } from '../../../core/services/order.service';
import { CustomerService } from '../../../core/services/customer.service';
import { ToastService } from '../../../core/services/toast.service';
import { Order, OrderStatus, Customer, Page } from '../../../models';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize } from 'rxjs/operators';
import { StatusBadgeComponent } from '../../../shared/components/status-badge/status-badge';
import { ConfirmationDialog } from '../../../shared/components/confirmation-dialog/confirmation-dialog';

@Component({
  selector: 'app-order-list',
  standalone: true,
  imports: [RouterModule, FormsModule, CurrencyPipe, DatePipe, KeyValuePipe, StatusBadgeComponent, ConfirmationDialog],
  templateUrl: './order-list.html',
  styleUrl: './order-list.scss'
})
export class OrderListComponent implements OnInit {
  private orderService = inject(OrderService);
  private customerService = inject(CustomerService);
  private toastService = inject(ToastService);
  private destroyRef = inject(DestroyRef);

  orders = signal<Order[]>([]);
  customers = signal(new Map<string, Customer>());
  loading = signal(true);

  currentPage = signal(0);
  pageSize = 10;
  totalElements = signal(0);
  totalPages = signal(0);

  selectedStatus: OrderStatus | '' = '';
  selectedCustomerId = '';
  startDate = '';
  endDate = '';

  pendingDeleteOrder = signal<Order | null>(null);

  OrderStatus = OrderStatus;
  Math = Math;

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

  ngOnInit(): void {
    this.loadOrders();
    this.loadCustomers();
  }

  loadOrders(): void {
    this.loading.set(true);

    const params: Record<string, unknown> = {
      page: this.currentPage(),
      size: this.pageSize,
      sort: 'createdAt,desc'
    };

    if (this.selectedStatus) params['status'] = this.selectedStatus;
    if (this.selectedCustomerId) params['customerId'] = this.selectedCustomerId;
    if (this.startDate) params['startDate'] = this.startDate;
    if (this.endDate) params['endDate'] = this.endDate;

    this.orderService.getOrders(params)
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.loading.set(false))
      )
      .subscribe({
        next: (page: Page<Order>) => {
          this.orders.set(page.content);
          this.totalElements.set(page.totalElements);
          this.totalPages.set(page.totalPages);
          this.currentPage.set(page.number);
        },
        error: (error) => {
          console.error('Failed to load orders:', error);
          this.toastService.error('Failed to load orders');
        }
      });
  }

  loadCustomers(): void {
    this.customerService.getCustomers({ page: 0, size: 1000 })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (page) => {
          const map = new Map<string, Customer>();
          page.content.forEach(customer => map.set(customer.id, customer));
          this.customers.set(map);
        },
        error: (error) => { console.error('Failed to load customers:', error); }
      });
  }

  onFilterChange(): void { this.currentPage.set(0); this.loadOrders(); }

  clearFilters(): void {
    this.selectedStatus = '';
    this.selectedCustomerId = '';
    this.startDate = '';
    this.endDate = '';
    this.currentPage.set(0);
    this.loadOrders();
  }

  goToPage(page: number): void {
    if (page >= 0 && page < this.totalPages()) { this.currentPage.set(page); this.loadOrders(); }
  }

  requestDelete(order: Order): void {
    if (order.status !== OrderStatus.DRAFT) {
      this.toastService.error('Only DRAFT orders can be deleted');
      return;
    }
    this.pendingDeleteOrder.set(order);
  }

  confirmDelete(): void {
    const order = this.pendingDeleteOrder();
    if (!order) return;
    this.pendingDeleteOrder.set(null);

    this.orderService.deleteOrder(order.id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => { this.toastService.success('Order deleted successfully'); this.loadOrders(); },
        error: (error) => {
          console.error('Failed to delete order:', error);
          this.toastService.error(error.error?.message || 'Failed to delete order');
        }
      });
  }

  cancelDelete(): void { this.pendingDeleteOrder.set(null); }

  getCustomer(customerId: string): Customer | undefined {
    return this.customers().get(customerId);
  }

  canEdit(order: Order): boolean   { return order.status === OrderStatus.DRAFT; }
  canDelete(order: Order): boolean { return order.status === OrderStatus.DRAFT; }
}

