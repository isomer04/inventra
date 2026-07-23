import { Component, OnInit, inject, DestroyRef, signal } from '@angular/core';
import { RouterModule, Router, ActivatedRoute } from '@angular/router';
import { FormBuilder, FormGroup, FormArray, Validators, ReactiveFormsModule } from '@angular/forms';
import { CurrencyPipe } from '@angular/common';
import { OrderService } from '../../../core/services/order.service';
import { CustomerService } from '../../../core/services/customer.service';
import { ProductService } from '../../../core/services/product.service';
import { ToastService } from '../../../core/services/toast.service';
import { OrderStatus, Customer, Product, Page, OrderCreateRequest } from '../../../models';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize } from 'rxjs/operators';

@Component({
  selector: 'app-order-form',
  standalone: true,
  imports: [RouterModule, ReactiveFormsModule, CurrencyPipe],
  templateUrl: './order-form.html',
  styleUrl: './order-form.scss'
})
export class OrderFormComponent implements OnInit {
  private fb = inject(FormBuilder);
  private orderService = inject(OrderService);
  private customerService = inject(CustomerService);
  private productService = inject(ProductService);
  private toastService = inject(ToastService);
  private router = inject(Router);
  private route = inject(ActivatedRoute);
  private destroyRef = inject(DestroyRef);

  orderForm!: FormGroup;

  customers = signal<Customer[]>([]);
  products = signal<Product[]>([]);
  // Create mode is immediately renderable; edit mode sets this while loading its order.
  loading = signal(false);
  submitting = signal(false);
  isEditMode = signal(false);
  orderId = signal<string | null>(null);

  ngOnInit(): void {
    this.initForm();
    this.loadCustomers();
    this.loadProducts();

    const id = this.route.snapshot.paramMap.get('id');
    this.orderId.set(id);
    if (id) {
      this.isEditMode.set(true);
      this.loadOrder(id);
    } else {
      this.addItem();
    }
  }

  initForm(): void {
    this.orderForm = this.fb.group({
      customerId: ['', Validators.required],
      notes: [''],
      items: this.fb.array([])
    });
  }

  loadCustomers(): void {
    this.customerService.getCustomers({ page: 0, size: 1000 })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (page) => { this.customers.set(page.content); },
        error: (error) => { console.error('Failed to load customers:', error); this.toastService.error('Failed to load customers'); }
      });
  }

  loadProducts(): void {
    this.productService.getProducts({ page: 0, size: 1000 })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (page: Page<Product>) => { this.products.set(page.content); },
        error: (error) => { console.error('Failed to load products:', error); this.toastService.error('Failed to load products'); }
      });
  }

  loadOrder(id: string): void {
    this.loading.set(true);
    this.orderService.getOrder(id)
      .pipe(takeUntilDestroyed(this.destroyRef), finalize(() => this.loading.set(false)))
      .subscribe({
        next: (order) => {
          if (order.status !== OrderStatus.DRAFT) {
            this.toastService.error('Only DRAFT orders can be edited');
            this.router.navigate(['/orders', order.id]);
            return;
          }
          this.orderForm.patchValue({ customerId: order.customerId, notes: order.notes });
          order.items.forEach(item => this.addItem(item.productId, item.quantity));
        },
        error: (error) => {
          console.error('Failed to load order:', error);
          this.toastService.error('Failed to load order');
          this.router.navigate(['/orders']);
        }
      });
  }

  get items(): FormArray { return this.orderForm.get('items') as FormArray; }

  createItemFormGroup(productId = '', quantity = 1): FormGroup {
    return this.fb.group({
      productId: [productId, Validators.required],
      quantity: [quantity, [Validators.required, Validators.min(1)]]
    });
  }

  addItem(productId = '', quantity = 1): void {
    this.items.push(this.createItemFormGroup(productId, quantity));
  }

  removeItem(index: number): void {
    this.items.removeAt(index);
  }

  getProduct(productId: string): Product | undefined {
    return this.products().find(p => p.id === productId);
  }

  getItemTotal(index: number): number {
    const item = this.items.at(index);
    const product = this.getProduct(item.get('productId')?.value);
    return product ? product.unitPrice * (item.get('quantity')?.value || 0) : 0;
  }

  getTotalAmount(): number {
    let total = 0;
    for (let i = 0; i < this.items.length; i++) total += this.getItemTotal(i);
    return total;
  }

  onSubmit(): void {
    if (this.orderForm.invalid) { this.orderForm.markAllAsTouched(); return; }
    if (this.items.length === 0) { this.toastService.error('Please add at least one item'); return; }

    this.submitting.set(true);
    const formValue = this.orderForm.value;

    const items = formValue.items.map((item: { productId: string; quantity: number }) => ({
      productId: item.productId,
      quantity: item.quantity
    }));

    // Prices are display-only on the client. The backend owns price locking at submit.
    const request: OrderCreateRequest = {
      customerId: formValue.customerId!,
      notes: formValue.notes || undefined,
      items
    };

    const id = this.orderId();
    const operation = this.isEditMode() && id
      ? this.orderService.updateOrder(id, request)
      : this.orderService.createOrder(request);

    operation
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (order) => {
          this.toastService.success(this.isEditMode() ? 'Order updated successfully' : 'Order created successfully');
          this.router.navigate(['/orders', order.id]);
        },
        error: (error) => {
          console.error('Failed to save order:', error);
          this.toastService.error(error.error?.message || 'Failed to save order');
          this.submitting.set(false);
        }
      });
  }

  onCancel(): void {
    const id = this.orderId();
    if (this.isEditMode() && id) {
      this.router.navigate(['/orders', id]);
    } else {
      this.router.navigate(['/orders']);
    }
  }

  get customerId() { return this.orderForm.get('customerId'); }
  get notes()      { return this.orderForm.get('notes'); }
}

