import { Component, OnInit, inject, DestroyRef, signal, input, output } from '@angular/core';
import { FormBuilder, FormGroup, Validators, ReactiveFormsModule } from '@angular/forms';
import { InventoryService } from '../../../core/services/inventory.service';
import { ToastService } from '../../../core/services/toast.service';
import { InventoryItem, Product, StockReceiptRequest } from '../../../models';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';

@Component({
  selector: 'app-stock-receipt-dialog',
  standalone: true,
  imports: [ReactiveFormsModule],
  templateUrl: './stock-receipt-dialog.html',
  styleUrl: './stock-receipt-dialog.scss'
})
export class StockReceiptDialogComponent implements OnInit {
  private fb = inject(FormBuilder);
  private inventoryService = inject(InventoryService);
  private toastService = inject(ToastService);
  private destroyRef = inject(DestroyRef);

  inventory = input.required<InventoryItem>();
  product   = input.required<Product>();
  closed    = output<void>();
  succeeded = output<void>();

  receiptForm!: FormGroup;
  submitting = signal(false);

  ngOnInit(): void {
    this.receiptForm = this.fb.group({
      quantity: [0, [Validators.required, Validators.min(1)]],
      notes: ['']
    });
  }

  onSubmit(): void {
    if (this.receiptForm.invalid) { this.receiptForm.markAllAsTouched(); return; }

    this.submitting.set(true);
    const request: StockReceiptRequest = this.receiptForm.value;

    this.inventoryService.receiveStock(this.product().id, request)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.toastService.success('Stock received successfully');
          this.succeeded.emit();
        },
        error: (error) => {
          console.error('Failed to receive stock:', error);
          this.toastService.error(error.error?.message || 'Failed to receive stock');
          this.submitting.set(false);
        }
      });
  }

  onCancel(): void { this.closed.emit(); }

  get quantity() { return this.receiptForm.get('quantity'); }
  get notes()    { return this.receiptForm.get('notes'); }
  get newTotal(): number { return this.inventory().quantityOnHand + (this.quantity?.value || 0); }
}
