import { Component, OnInit, inject, DestroyRef, signal } from '@angular/core';
import { RouterModule, Router, ActivatedRoute } from '@angular/router';
import { FormBuilder, FormGroup, Validators, ReactiveFormsModule } from '@angular/forms';
import { CustomerService } from '../../../core/services/customer.service';
import { ToastService } from '../../../core/services/toast.service';
import { CustomerStatus, CustomerCreateRequest, CustomerUpdateRequest } from '../../../models';
import { finalize } from 'rxjs/operators';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';

@Component({
  selector: 'app-customer-form',
  standalone: true,
  imports: [RouterModule, ReactiveFormsModule],
  templateUrl: './customer-form.html',
  styleUrl: './customer-form.scss'
})
export class CustomerFormComponent implements OnInit {
  private fb = inject(FormBuilder);
  private customerService = inject(CustomerService);
  private toastService = inject(ToastService);
  private router = inject(Router);
  private route = inject(ActivatedRoute);
  private destroyRef = inject(DestroyRef);

  customerForm!: FormGroup;
  // Create mode is immediately renderable; edit mode sets this while loading its customer.
  loading = signal(false);
  submitting = signal(false);
  isEditMode = signal(false);
  customerId: string | null = null;

  CustomerStatus = CustomerStatus;

  ngOnInit(): void {
    this.initForm();
    this.customerId = this.route.snapshot.paramMap.get('id');
    if (this.customerId) {
      this.isEditMode.set(true);
      this.loadCustomer(this.customerId);
    } else {
      this.loading.set(false);
    }
  }

  initForm(): void {
    this.customerForm = this.fb.group({
      name:    ['', [Validators.required, Validators.maxLength(200)]],
      email:   ['', [Validators.email, Validators.maxLength(150)]],
      phone:   ['', Validators.maxLength(30)],
      address: [''],
      notes:   [''],
      status:  [CustomerStatus.ACTIVE, Validators.required]
    });
  }

  loadCustomer(id: string): void {
    this.loading.set(true);
    this.customerService.getCustomer(id)
      .pipe(takeUntilDestroyed(this.destroyRef), finalize(() => this.loading.set(false)))
      .subscribe({
        next: (customer) => {
          this.customerForm.patchValue({
            name: customer.name, email: customer.email, phone: customer.phone,
            address: customer.address, notes: customer.notes, status: customer.status
          });
        },
        error: (error) => {
          console.error('Failed to load customer:', error);
          this.toastService.error('Failed to load customer');
          this.router.navigate(['/customers']);
        }
      });
  }

  onSubmit(): void {
    if (this.customerForm.invalid) { this.customerForm.markAllAsTouched(); return; }

    this.submitting.set(true);
    const formValue = this.customerForm.value;

    const request: CustomerCreateRequest | CustomerUpdateRequest = {
      name:    formValue.name!,
      email:   formValue.email   || undefined,
      phone:   formValue.phone   || undefined,
      address: formValue.address || undefined,
      notes:   formValue.notes   || undefined,
      status:  formValue.status
    };

    const operation = this.isEditMode() && this.customerId
      ? this.customerService.updateCustomer(this.customerId, request as CustomerUpdateRequest)
      : this.customerService.createCustomer(request as CustomerCreateRequest);

    operation
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (customer) => {
          this.toastService.success(this.isEditMode() ? 'Customer updated successfully' : 'Customer created successfully');
          this.router.navigate(['/customers', customer.id]);
        },
        error: (error) => {
          console.error('Failed to save customer:', error);
          this.toastService.error(error.error?.message || 'Failed to save customer');
          this.submitting.set(false);
        }
      });
  }

  onCancel(): void {
    if (this.isEditMode() && this.customerId) {
      this.router.navigate(['/customers', this.customerId]);
    } else {
      this.router.navigate(['/customers']);
    }
  }

  get name()    { return this.customerForm.get('name'); }
  get email()   { return this.customerForm.get('email'); }
  get phone()   { return this.customerForm.get('phone'); }
  get address() { return this.customerForm.get('address'); }
  get notes()   { return this.customerForm.get('notes'); }
  get status()  { return this.customerForm.get('status'); }
}

