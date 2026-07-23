import { Component, OnInit, inject, DestroyRef, signal, computed } from '@angular/core';
import { RouterModule } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { CustomerService } from '../../../core/services/customer.service';
import { ToastService } from '../../../core/services/toast.service';
import { Customer, CustomerStatus, Page } from '../../../models';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize } from 'rxjs/operators';
import { StatusBadgeComponent } from '../../../shared/components/status-badge/status-badge';
import { ConfirmationDialog } from '../../../shared/components/confirmation-dialog/confirmation-dialog';

@Component({
  selector: 'app-customer-list',
  standalone: true,
  imports: [RouterModule, FormsModule, StatusBadgeComponent, ConfirmationDialog],
  templateUrl: './customer-list.html',
  styleUrl: './customer-list.scss'
})
export class CustomerListComponent implements OnInit {
  private customerService = inject(CustomerService);
  private toastService = inject(ToastService);
  private destroyRef = inject(DestroyRef);

  customers = signal<Customer[]>([]);
  loading = signal(true);

  currentPage = signal(0);
  pageSize = 10;
  totalElements = signal(0);
  totalPages = signal(0);

  searchTerm = '';
  selectedStatus: CustomerStatus | '' = '';

  pendingDeleteCustomer = signal<Customer | null>(null);

  CustomerStatus = CustomerStatus;
  Math = Math;

  ngOnInit(): void {
    this.loadCustomers();
  }

  loadCustomers(): void {
    this.loading.set(true);

    const params: Record<string, unknown> = {
      page: this.currentPage(),
      size: this.pageSize,
      sort: 'name,asc'
    };

    if (this.searchTerm) params['search'] = this.searchTerm;
    if (this.selectedStatus) params['status'] = this.selectedStatus;

    this.customerService.getCustomers(params)
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.loading.set(false))
      )
      .subscribe({
        next: (page: Page<Customer>) => {
          this.customers.set(page.content);
          this.totalElements.set(page.totalElements);
          this.totalPages.set(page.totalPages);
          this.currentPage.set(page.number);
        },
        error: (error) => {
          console.error('Failed to load customers:', error);
          this.toastService.error('Failed to load customers');
        }
      });
  }

  onSearch(): void { this.currentPage.set(0); this.loadCustomers(); }
  onFilterChange(): void { this.currentPage.set(0); this.loadCustomers(); }

  clearFilters(): void {
    this.searchTerm = '';
    this.selectedStatus = '';
    this.currentPage.set(0);
    this.loadCustomers();
  }

  goToPage(page: number): void {
    if (page >= 0 && page < this.totalPages()) { this.currentPage.set(page); this.loadCustomers(); }
  }

  requestDelete(customer: Customer): void {
    this.pendingDeleteCustomer.set(customer);
  }

  confirmDelete(): void {
    const customer = this.pendingDeleteCustomer();
    if (!customer) return;
    this.pendingDeleteCustomer.set(null);

    this.customerService.deleteCustomer(customer.id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => { this.toastService.success('Customer deleted successfully'); this.loadCustomers(); },
        error: (error) => {
          console.error('Failed to delete customer:', error);
          this.toastService.error(error.error?.message || 'Failed to delete customer');
        }
      });
  }

  cancelDelete(): void { this.pendingDeleteCustomer.set(null); }

  pageNumbers = computed(() => {
    const pages: number[] = [];
    const maxPages = 5;
    let startPage = Math.max(0, this.currentPage() - Math.floor(maxPages / 2));
    const endPage = Math.min(this.totalPages() - 1, startPage + maxPages - 1);
    if (endPage - startPage < maxPages - 1) startPage = Math.max(0, endPage - maxPages + 1);
    for (let i = startPage; i <= endPage; i++) pages.push(i);
    return pages;
  });
}

