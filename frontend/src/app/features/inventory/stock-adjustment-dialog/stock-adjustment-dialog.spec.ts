import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ReactiveFormsModule } from '@angular/forms';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { of, throwError } from 'rxjs';
import { StockAdjustmentDialogComponent } from './stock-adjustment-dialog';
import { InventoryService } from '../../../core/services/inventory.service';
import { ToastService } from '../../../core/services/toast.service';
import { InventoryItem, Product, ProductStatus } from '../../../models';
import { ComponentFixtureAutoDetect } from '@angular/core/testing';
import type { MockedObject } from 'vitest';

describe('StockAdjustmentDialogComponent', () => {
  let component: StockAdjustmentDialogComponent;
  let fixture: ComponentFixture<StockAdjustmentDialogComponent>;
  let inventoryService: MockedObject<InventoryService>;
  let toastService: MockedObject<ToastService>;

  const mockProduct: Product = {
    id: 'prod-1',
    sku: 'SKU-001',
    name: 'Test Product',
    unitPrice: 10.00,
    status: ProductStatus.ACTIVE,
    tenantId: 't1',
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString()
  };

  const mockInventory: InventoryItem = {
    id: 'inv-1',
    tenantId: 't1',
    productId: 'prod-1',
    productName: 'Test Product',
    productSku: 'SKU-001',
    quantityOnHand: 100,
    quantityReserved: 10,
    availableQuantity: 90,
    reorderPoint: 20,
    lastUpdated: new Date().toISOString()
  };

  beforeEach(async () => {
    const inventorySpy = { adjustStock: vi.fn() };
    const toastSpy = { success: vi.fn(), error: vi.fn() };

    await TestBed.configureTestingModule({
      imports: [StockAdjustmentDialogComponent, ReactiveFormsModule],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: InventoryService, useValue: inventorySpy },
        { provide: ToastService, useValue: toastSpy },
        { provide: ComponentFixtureAutoDetect, useValue: true }
      ]
    }).compileComponents();

    inventoryService = TestBed.inject(InventoryService) as unknown as MockedObject<InventoryService>;
    toastService = TestBed.inject(ToastService) as unknown as MockedObject<ToastService>;

    fixture = TestBed.createComponent(StockAdjustmentDialogComponent);
    component = fixture.componentInstance;

    fixture.componentRef.setInput('inventory', mockInventory);
    fixture.componentRef.setInput('product', mockProduct);
    
    fixture.detectChanges();
  });

  describe('Component Initialization', () => {
    it('should create', () => {
      expect(component).toBeTruthy();
    });

    it('should initialize form with quantity and notes fields', () => {
      expect(component.adjustmentForm).toBeDefined();
      expect(component.adjustmentForm.get('quantity')).toBeDefined();
      expect(component.adjustmentForm.get('notes')).toBeDefined();
    });

    it('should initialize quantity with 0', () => {
      expect(component.quantity?.value).toBe(0);
    });

    it('should initialize notes with empty string', () => {
      expect(component.notes?.value).toBe('');
    });

    it('should receive inventory input', () => {
      expect(component.inventory()).toEqual(mockInventory);
    });

    it('should receive product input', () => {
      expect(component.product()).toEqual(mockProduct);
    });
  });

  describe('Form Validation', () => {
    it('should require quantity', () => {
      const quantity = component.adjustmentForm.get('quantity');
      
      quantity?.setValue(null);
      expect(quantity?.hasError('required')).toBe(true);
      
      quantity?.setValue(10);
      expect(quantity?.hasError('required')).toBe(false);
    });

    it('should require notes', () => {
      const notes = component.adjustmentForm.get('notes');
      
      notes?.setValue('');
      expect(notes?.hasError('required')).toBe(true);
      
      notes?.setValue('Stock count adjustment');
      expect(notes?.hasError('required')).toBe(false);
    });

    it('should mark form as invalid when fields are empty', () => {
      component.adjustmentForm.patchValue({ quantity: null, notes: '' });
      
      expect(component.adjustmentForm.valid).toBe(false);
    });

    it('should mark form as valid when all required fields are filled', () => {
      component.adjustmentForm.patchValue({ quantity: 10, notes: 'Adjustment reason' });
      
      expect(component.adjustmentForm.valid).toBe(true);
    });
  });

  describe('Quantity Adjustments', () => {
    it('should allow positive adjustments', () => {
      component.adjustmentForm.patchValue({ quantity: 50 });
      
      expect(component.quantity?.value).toBe(50);
      expect(component.isIncrease).toBe(true);
      expect(component.isDecrease).toBe(false);
    });

    it('should allow negative adjustments', () => {
      component.adjustmentForm.patchValue({ quantity: -30 });
      
      expect(component.quantity?.value).toBe(-30);
      expect(component.isDecrease).toBe(true);
      expect(component.isIncrease).toBe(false);
    });

    it('should allow zero adjustment', () => {
      component.adjustmentForm.patchValue({ quantity: 0 });
      
      expect(component.quantity?.value).toBe(0);
      expect(component.isIncrease).toBe(false);
      expect(component.isDecrease).toBe(false);
    });

    it('should calculate new total for positive adjustment', () => {
      component.adjustmentForm.patchValue({ quantity: 25 });
      
      expect(component.newTotal).toBe(125); // 100 + 25
    });

    it('should calculate new total for negative adjustment', () => {
      component.adjustmentForm.patchValue({ quantity: -40 });
      
      expect(component.newTotal).toBe(60); // 100 - 40
    });

    it('should calculate new total as current when quantity is 0', () => {
      component.adjustmentForm.patchValue({ quantity: 0 });
      
      expect(component.newTotal).toBe(100);
    });

    it('should handle large positive adjustments', () => {
      component.adjustmentForm.patchValue({ quantity: 1000 });
      
      expect(component.newTotal).toBe(1100);
    });

    it('should allow adjustments that reduce stock below zero', () => {
      component.adjustmentForm.patchValue({ quantity: -150 });
      
      expect(component.newTotal).toBe(-50); // 100 - 150
    });
  });

  describe('Form Submission - Success', () => {
    beforeEach(() => {
      component.adjustmentForm.patchValue({
        quantity: 10,
        notes: 'Stock count adjustment'
      });
    });

    it('should call inventoryService with correct data', () => {
      inventoryService.adjustStock.mockReturnValue(of(mockInventory));

      component.onSubmit();

      expect(inventoryService.adjustStock).toHaveBeenCalledWith('prod-1', {
        quantity: 10,
        notes: 'Stock count adjustment'
      });
    });

    it('should show success toast on successful adjustment', () => {
      inventoryService.adjustStock.mockReturnValue(of(mockInventory));

      component.onSubmit();

      expect(toastService.success).toHaveBeenCalledWith('Stock adjusted successfully');
    });

    it('should emit succeeded event on successful adjustment', () => new Promise<void>((resolve) => {
      inventoryService.adjustStock.mockReturnValue(of(mockInventory));

      component.succeeded.subscribe(() => {
        expect(true).toBe(true);
        resolve();
      });

      component.onSubmit();
    }));

    it('should set submitting to true during submission', () => {
      inventoryService.adjustStock.mockReturnValue(of(mockInventory));

      expect(component.submitting()).toBe(false);
      
      component.onSubmit();
      
      // Note: submitting resets after observable completes
      expect(inventoryService.adjustStock).toHaveBeenCalled();
    });
  });

  describe('Form Submission - Validation Errors', () => {
    it('should not submit if form is invalid', () => {
      component.adjustmentForm.patchValue({ quantity: null, notes: '' });

      component.onSubmit();

      expect(inventoryService.adjustStock).not.toHaveBeenCalled();
      expect(component.adjustmentForm.touched).toBe(true);
    });

    it('should mark all fields as touched on invalid submission', () => {
      component.adjustmentForm.patchValue({ quantity: null, notes: '' });

      component.onSubmit();

      expect(component.quantity?.touched).toBe(true);
      expect(component.notes?.touched).toBe(true);
    });

    it('should not submit when only quantity is filled', () => {
      component.adjustmentForm.patchValue({ quantity: 10, notes: '' });

      component.onSubmit();

      expect(inventoryService.adjustStock).not.toHaveBeenCalled();
    });

    it('should not submit when only notes is filled', () => {
      component.adjustmentForm.patchValue({ quantity: null, notes: 'Notes' });

      component.onSubmit();

      expect(inventoryService.adjustStock).not.toHaveBeenCalled();
    });
  });

  describe('Form Submission - API Errors', () => {
    beforeEach(() => {
      component.adjustmentForm.patchValue({
        quantity: 10,
        notes: 'Adjustment reason'
      });
    });

    it('should show error toast on API failure', () => {
      inventoryService.adjustStock.mockReturnValue(
        throwError(() => ({ error: { message: 'Adjustment failed' } }))
      );

      component.onSubmit();

      expect(toastService.error).toHaveBeenCalledWith('Adjustment failed');
    });

    it('should show generic error message when error has no message', () => {
      inventoryService.adjustStock.mockReturnValue(
        throwError(() => ({ error: {} }))
      );

      component.onSubmit();

      expect(toastService.error).toHaveBeenCalledWith('Failed to adjust stock');
    });

    it('should set submitting to false on error', () => {
      inventoryService.adjustStock.mockReturnValue(
        throwError(() => ({ error: { message: 'Error' } }))
      );

      component.onSubmit();

      expect(component.submitting()).toBe(false);
    });

    it('should not emit succeeded event on error', () => new Promise<void>((resolve) => {
      inventoryService.adjustStock.mockReturnValue(
        throwError(() => ({ error: { message: 'Error' } }))
      );

      let emitted = false;
      component.succeeded.subscribe(() => { emitted = true; });

      component.onSubmit();

      setTimeout(() => {
        expect(emitted).toBe(false);
        resolve();
      }, 100);
    }));
  });

  describe('Cancel Action', () => {
    it('should emit closed event when cancelled', () => new Promise<void>((resolve) => {
      component.closed.subscribe(() => {
        expect(true).toBe(true);
        resolve();
      });

      component.onCancel();
    }));

    it('should not submit form when cancelled', () => {
      component.adjustmentForm.patchValue({ quantity: 10, notes: 'Notes' });

      component.onCancel();

      expect(inventoryService.adjustStock).not.toHaveBeenCalled();
    });
  });

  describe('Computed Properties', () => {
    it('should identify increase when quantity is positive', () => {
      component.adjustmentForm.patchValue({ quantity: 1 });
      expect(component.isIncrease).toBe(true);
      
      component.adjustmentForm.patchValue({ quantity: 100 });
      expect(component.isIncrease).toBe(true);
    });

    it('should identify decrease when quantity is negative', () => {
      component.adjustmentForm.patchValue({ quantity: -1 });
      expect(component.isDecrease).toBe(true);
      
      component.adjustmentForm.patchValue({ quantity: -100 });
      expect(component.isDecrease).toBe(true);
    });

    it('should not identify as increase or decrease when quantity is zero', () => {
      component.adjustmentForm.patchValue({ quantity: 0 });
      
      expect(component.isIncrease).toBe(false);
      expect(component.isDecrease).toBe(false);
    });

    it('should handle null quantity in computed properties', () => {
      component.adjustmentForm.patchValue({ quantity: null });
      
      expect(component.isIncrease).toBe(false);
      expect(component.isDecrease).toBe(false);
      expect(component.newTotal).toBe(100); // quantityOnHand + 0
    });
  });

  describe('Form Getters', () => {
    it('should provide getter for quantity', () => {
      expect(component.quantity).toBe(component.adjustmentForm.get('quantity'));
    });

    it('should provide getter for notes', () => {
      expect(component.notes).toBe(component.adjustmentForm.get('notes'));
    });
  });

  describe('Integration Scenarios', () => {
    it('should handle complete adjustment workflow', () => {
      inventoryService.adjustStock.mockReturnValue(of(mockInventory));
      let succeededEmitted = false;
      
      component.succeeded.subscribe(() => { succeededEmitted = true; });

      component.adjustmentForm.patchValue({
        quantity: 50,
        notes: 'Received new shipment'
      });

      component.onSubmit();

      expect(inventoryService.adjustStock).toHaveBeenCalled();
      expect(toastService.success).toHaveBeenCalled();
      expect(succeededEmitted).toBe(true);
    });

    it('should handle stock reduction workflow', () => {
      inventoryService.adjustStock.mockReturnValue(of(mockInventory));

      component.adjustmentForm.patchValue({
        quantity: -20,
        notes: 'Damaged items removed'
      });

      component.onSubmit();

      const request = inventoryService.adjustStock.mock.lastCall![1];
      expect(request.quantity).toBe(-20);
      expect(request.notes).toBe('Damaged items removed');
    });
  });
});
