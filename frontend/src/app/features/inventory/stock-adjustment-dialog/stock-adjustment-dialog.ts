import { Component, OnInit, inject, DestroyRef, signal, input, output } from '@angular/core';
import { FormBuilder, FormGroup, Validators, ReactiveFormsModule } from '@angular/forms';
import { InventoryService } from '../../../core/services/inventory.service';
import { ToastService } from '../../../core/services/toast.service';
import { InventoryItem, Product, StockAdjustmentRequest } from '../../../models';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';

@Component({
  selector: 'app-stock-adjustment-dialog',
  standalone: true,
  imports: [ReactiveFormsModule],
  templateUrl: './stock-adjustment-dialog.html',
  styleUrl: './stock-adjustment-dialog.scss'
})
export class StockAdjustmentDialogComponent implements OnInit {
  private fb = inject(FormBuilder);
  private inventoryService = inject(InventoryService);
  private toastService = inject(ToastService);
  private destroyRef = inject(DestroyRef);

  inventory = input.required<InventoryItem>();
  product   = input.required<Product>();
  closed    = output<void>();
  succeeded = output<void>();

  adjustmentForm!: FormGroup;
  submitting = signal(false);
  Math = Math;

  ngOnInit(): void {
    this.adjustmentForm = this.fb.group({
      quantity: [0, [Validators.required]],
      notes: ['', Validators.required]
    });
  }

  onSubmit(): void {
    if (this.adjustmentForm.invalid) { this.adjustmentForm.markAllAsTouched(); return; }

    this.submitting.set(true);
    const request: StockAdjustmentRequest = this.adjustmentForm.value;

    this.inventoryService.adjustStock(this.product().id, request)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.toastService.success('Stock adjusted successfully');
          this.succeeded.emit();
        },
        error: (error) => {
          console.error('Failed to adjust stock:', error);
          this.toastService.error(error.error?.message || 'Failed to adjust stock');
          this.submitting.set(false);
        }
      });
  }

  onCancel(): void { this.closed.emit(); }

  get quantity() { return this.adjustmentForm.get('quantity'); }
  get notes()    { return this.adjustmentForm.get('notes'); }
  get newTotal(): number { return this.inventory().quantityOnHand + (this.quantity?.value || 0); }
  get isIncrease(): boolean { return (this.quantity?.value || 0) > 0; }
  get isDecrease(): boolean { return (this.quantity?.value || 0) < 0; }
}
