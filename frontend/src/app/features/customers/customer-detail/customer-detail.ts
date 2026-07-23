import { Component, OnInit, inject, DestroyRef, signal } from '@angular/core';
import { RouterModule, Router, ActivatedRoute } from '@angular/router';
import { DatePipe } from '@angular/common';
import { CustomerService } from '../../../core/services/customer.service';
import { ToastService } from '../../../core/services/toast.service';
import { Customer, CustomerStatus } from '../../../models';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize } from 'rxjs/operators';
import { StatusBadgeComponent } from '../../../shared/components/status-badge/status-badge';
import { ConfirmationDialog } from '../../../shared/components/confirmation-dialog/confirmation-dialog';

@Component({
  selector: 'app-customer-detail',
  standalone: true,
  imports: [RouterModule, DatePipe, StatusBadgeComponent, ConfirmationDialog],
  templateUrl: './customer-detail.html',
  styleUrl: './customer-detail.scss'
})
export class CustomerDetailComponent implements OnInit {
  private customerService = inject(CustomerService);
  private toastService = inject(ToastService);
  private router = inject(Router);
  private route = inject(ActivatedRoute);
  private destroyRef = inject(DestroyRef);

  customer = signal<Customer | null>(null);
  loading = signal(true);
  pendingDelete = signal<Customer | null>(null);

  CustomerStatus = CustomerStatus;

  ngOnInit(): void {
    const customerId = this.route.snapshot.paramMap.get('id');
    if (customerId) {
      this.loadCustomer(customerId);
    } else {
      this.router.navigate(['/customers']);
    }
  }

  loadCustomer(id: string): void {
    this.loading.set(true);
    this.customerService.getCustomer(id)
      .pipe(takeUntilDestroyed(this.destroyRef), finalize(() => this.loading.set(false)))
      .subscribe({
        next: (customer) => { this.customer.set(customer); },
        error: (error) => {
          console.error('Failed to load customer:', error);
          this.toastService.error('Failed to load customer');
          this.router.navigate(['/customers']);
        }
      });
  }

  requestDelete(): void {
    const current = this.customer();
    if (current) this.pendingDelete.set(current);
  }

  confirmDelete(): void {
    const current = this.pendingDelete();
    if (!current) return;
    this.pendingDelete.set(null);

    this.customerService.deleteCustomer(current.id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.toastService.success('Customer deleted successfully');
          this.router.navigate(['/customers']);
        },
        error: (error) => {
          console.error('Failed to delete customer:', error);
          this.toastService.error(error.error?.message || 'Failed to delete customer');
        }
      });
  }

  cancelDelete(): void {
    this.pendingDelete.set(null);
  }
}

