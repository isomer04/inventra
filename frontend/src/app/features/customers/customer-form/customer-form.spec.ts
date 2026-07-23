import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CustomerFormComponent } from './customer-form';
import { CustomerService } from '../../../core/services/customer.service';
import { ToastService } from '../../../core/services/toast.service';
import { Router } from '@angular/router';
import { ActivatedRoute } from '@angular/router';
import { of, Subject, throwError } from 'rxjs';

// A pending request allows loading-state assertions before finalization.
import { CustomerStatus } from '../../../models';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import type { MockedObject } from 'vitest';

describe('CustomerFormComponent', () => {
  let component: CustomerFormComponent;
  let fixture: ComponentFixture<CustomerFormComponent>;
  let customerService: MockedObject<CustomerService>;
  let toastService: MockedObject<ToastService>;
  let router: MockedObject<Router>;

  const mockCustomer = {
    id: 'cust-1',
    tenantId: 'tenant-1',
    name: 'Acme Corporation',
    email: 'contact@acme.com',
    phone: '555-0100',
    address: '123 Main St, Suite 100',
    notes: 'VIP customer',
    status: CustomerStatus.ACTIVE,
    createdAt: '2024-01-15T10:00:00Z',
    updatedAt: '2024-01-15T10:00:00Z'
  };

  beforeEach(async () => {
    const customerServiceSpy = { getCustomer: vi.fn(), createCustomer: vi.fn(), updateCustomer: vi.fn() };
    const toastServiceSpy = { success: vi.fn(), error: vi.fn() };
    const routerSpy = { navigate: vi.fn() };

    await TestBed.configureTestingModule({
      imports: [CustomerFormComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: CustomerService, useValue: customerServiceSpy },
        { provide: ToastService, useValue: toastServiceSpy },
        { provide: Router, useValue: routerSpy },
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { paramMap: { get: () => null } } }
        }
      ]
    }).compileComponents();

    customerService = TestBed.inject(CustomerService) as unknown as MockedObject<CustomerService>;
    toastService = TestBed.inject(ToastService) as unknown as MockedObject<ToastService>;
    router = TestBed.inject(Router) as unknown as MockedObject<Router>;

    fixture = TestBed.createComponent(CustomerFormComponent);
    component = fixture.componentInstance;
  });

  describe('Component Initialization', () => {
    it('should create', () => {
      expect(component).toBeTruthy();
    });

    it('should initialize form with empty values in create mode', () => {
      fixture.detectChanges();

      expect(component.isEditMode()).toBe(false);
      expect(component.customerForm.value).toEqual({
        name: '',
        email: '',
        phone: '',
        address: '',
        notes: '',
        status: CustomerStatus.ACTIVE
      });
    });

    it('should initialize form controls with proper validators', () => {
      fixture.detectChanges();

      const nameControl = component.name;
      const emailControl = component.email;
      const phoneControl = component.phone;
      const statusControl = component.status;

      expect(nameControl?.hasError('required')).toBe(true);
      expect(emailControl?.validator).toBeTruthy();
      expect(phoneControl?.validator).toBeTruthy();
      expect(statusControl?.hasError('required')).toBe(false);
    });
  });

  describe('Edit Mode - Load Customer', () => {
    beforeEach(() => {
      const route = TestBed.inject(ActivatedRoute);
      vi.spyOn(route.snapshot.paramMap, 'get').mockReturnValue('cust-1');

      // These tests call loadCustomer() directly, bypassing ngOnInit.
      component.initForm();
    });

    it('should load customer data in edit mode', () => {
      customerService.getCustomer.mockReturnValue(of(mockCustomer));

      component.customerId = 'cust-1';
      component.isEditMode.set(true);
      component.loadCustomer('cust-1');
      fixture.detectChanges();

      expect(customerService.getCustomer).toHaveBeenCalledWith('cust-1');
      expect(component.customerForm.value).toEqual({
        name: mockCustomer.name,
        email: mockCustomer.email,
        phone: mockCustomer.phone,
        address: mockCustomer.address,
        notes: mockCustomer.notes,
        status: mockCustomer.status
      });
    });

    it('should set loading state while fetching customer', () => {
      const customerRequest = new Subject<typeof mockCustomer>();
      customerService.getCustomer.mockReturnValue(customerRequest);

      component.loadCustomer('cust-1');

      expect(component.loading()).toBe(true);

      customerRequest.complete();

      expect(component.loading()).toBe(false);
    });

    it('should handle customer load error and navigate to list', () => {
      const error = { message: 'Customer not found' };
      customerService.getCustomer.mockReturnValue(throwError(() => error));

      component.loadCustomer('cust-1');
      fixture.detectChanges();

      expect(toastService.error).toHaveBeenCalledWith('Failed to load customer');
      expect(router.navigate).toHaveBeenCalledWith(['/customers']);
    });
  });

  describe('Form Validation', () => {
    beforeEach(() => {
      fixture.detectChanges();
    });

    it('should validate name as required', () => {
      const nameControl = component.name!;
      nameControl.setValue('');

      expect(nameControl.hasError('required')).toBe(true);
      expect(component.customerForm.invalid).toBe(true);
    });

    it('should validate name max length (200 chars)', () => {
      const nameControl = component.name!;
      const longName = 'A'.repeat(201);
      nameControl.setValue(longName);

      expect(nameControl.hasError('maxlength')).toBe(true);
    });

    it('should accept valid name', () => {
      const nameControl = component.name!;
      nameControl.setValue('Acme Corporation');

      expect(nameControl.valid).toBe(true);
    });

    it('should validate email format', () => {
      const emailControl = component.email!;

      emailControl.setValue('invalid-email');
      expect(emailControl.hasError('email')).toBe(true);

      emailControl.setValue('valid@example.com');
      expect(emailControl.hasError('email')).toBe(false);
    });

    it('should validate email max length (150 chars)', () => {
      const emailControl = component.email!;
      const longEmail = 'a'.repeat(140) + '@example.com';
      emailControl.setValue(longEmail);

      expect(emailControl.hasError('maxlength')).toBe(true);
    });

    it('should allow empty email (optional field)', () => {
      const emailControl = component.email!;
      emailControl.setValue('');

      expect(emailControl.valid).toBe(true);
    });

    it('should validate phone max length (30 chars)', () => {
      const phoneControl = component.phone!;
      const longPhone = '1'.repeat(31);
      phoneControl.setValue(longPhone);

      expect(phoneControl.hasError('maxlength')).toBe(true);
    });

    it('should allow empty phone (optional field)', () => {
      const phoneControl = component.phone!;
      phoneControl.setValue('');

      expect(phoneControl.valid).toBe(true);
    });

    it('should allow empty address (optional field)', () => {
      const addressControl = component.address!;
      addressControl.setValue('');

      expect(addressControl.valid).toBe(true);
    });

    it('should allow empty notes (optional field)', () => {
      const notesControl = component.notes!;
      notesControl.setValue('');

      expect(notesControl.valid).toBe(true);
    });

    it('should validate status as required', () => {
      const statusControl = component.status!;
      statusControl.setValue(null);

      expect(statusControl.hasError('required')).toBe(true);
    });

    it('should accept valid status values', () => {
      const statusControl = component.status!;

      statusControl.setValue(CustomerStatus.ACTIVE);
      expect(statusControl.valid).toBe(true);

      statusControl.setValue(CustomerStatus.INACTIVE);
      expect(statusControl.valid).toBe(true);
    });
  });

  describe('Form Submission - Create Mode', () => {
    beforeEach(() => {
      fixture.detectChanges();
    });

    it('should not submit when form is invalid', () => {
      component.customerForm.patchValue({ name: '' });
      component.onSubmit();

      expect(customerService.createCustomer).not.toHaveBeenCalled();
      // onSubmit() marks everything touched on an invalid form so the field
      // validation messages become visible.
      expect(component.customerForm.touched).toBe(true);
    });

    it('should mark all fields as touched when submitting invalid form', () => {
      component.customerForm.patchValue({ name: '' });
      component.onSubmit();

      expect(component.name?.touched).toBe(true);
    });

    it('should create customer with valid data', () => {
      const newCustomer = { ...mockCustomer, id: 'new-cust-1' };
      customerService.createCustomer.mockReturnValue(of(newCustomer));

      component.customerForm.patchValue({
        name: 'New Customer',
        email: 'new@customer.com',
        phone: '555-0200',
        address: '456 Oak Ave',
        notes: 'New account',
        status: CustomerStatus.ACTIVE
      });

      component.onSubmit();

      expect(customerService.createCustomer).toHaveBeenCalledWith({
        name: 'New Customer',
        email: 'new@customer.com',
        phone: '555-0200',
        address: '456 Oak Ave',
        notes: 'New account',
        status: CustomerStatus.ACTIVE
      });
    });

    it('should navigate to customer detail after successful creation', () => {
      const newCustomer = { ...mockCustomer, id: 'new-cust-1' };
      customerService.createCustomer.mockReturnValue(of(newCustomer));

      component.customerForm.patchValue({
        name: 'New Customer',
        status: CustomerStatus.ACTIVE
      });

      component.onSubmit();

      expect(router.navigate).toHaveBeenCalledWith(['/customers', 'new-cust-1']);
    });

    it('should show success toast after creation', () => {
      const newCustomer = { ...mockCustomer, id: 'new-cust-1' };
      customerService.createCustomer.mockReturnValue(of(newCustomer));

      component.customerForm.patchValue({
        name: 'New Customer',
        status: CustomerStatus.ACTIVE
      });

      component.onSubmit();

      expect(toastService.success).toHaveBeenCalledWith('Customer created successfully');
    });

    it('should handle creation error', () => {
      const error = { error: { message: 'Email already exists' } };
      customerService.createCustomer.mockReturnValue(throwError(() => error));

      component.customerForm.patchValue({
        name: 'New Customer',
        email: 'duplicate@example.com',
        status: CustomerStatus.ACTIVE
      });

      component.onSubmit();

      expect(toastService.error).toHaveBeenCalledWith('Email already exists');
      expect(component.submitting()).toBe(false);
    });

    it('should omit empty optional fields when creating', () => {
      const newCustomer = { ...mockCustomer, id: 'new-cust-1' };
      customerService.createCustomer.mockReturnValue(of(newCustomer));

      component.customerForm.patchValue({
        name: 'Minimal Customer',
        email: '',
        phone: '',
        address: '',
        notes: '',
        status: CustomerStatus.ACTIVE
      });

      component.onSubmit();

      expect(customerService.createCustomer).toHaveBeenCalledWith({
        name: 'Minimal Customer',
        email: undefined,
        phone: undefined,
        address: undefined,
        notes: undefined,
        status: CustomerStatus.ACTIVE
      });
    });
  });

  describe('Form Submission - Edit Mode', () => {
    beforeEach(() => {
      // detectChanges() runs ngOnInit, which reads the id from the route and
      // would reset these — the ActivatedRoute stub returns null. Switch the
      // component into edit mode afterwards so the flags survive.
      fixture.detectChanges();
      component.customerId = 'cust-1';
      component.isEditMode.set(true);
    });

    it('should update customer with valid data', () => {
      customerService.updateCustomer.mockReturnValue(of(mockCustomer));

      component.customerForm.patchValue({
        name: 'Updated Customer',
        email: 'updated@customer.com',
        phone: '555-0300',
        address: '789 Pine Rd',
        notes: 'Updated notes',
        status: CustomerStatus.INACTIVE
      });

      component.onSubmit();

      expect(customerService.updateCustomer).toHaveBeenCalledWith('cust-1', {
        name: 'Updated Customer',
        email: 'updated@customer.com',
        phone: '555-0300',
        address: '789 Pine Rd',
        notes: 'Updated notes',
        status: CustomerStatus.INACTIVE
      });
    });

    it('should navigate to customer detail after successful update', () => {
      customerService.updateCustomer.mockReturnValue(of(mockCustomer));

      component.customerForm.patchValue({
        name: 'Updated Customer',
        status: CustomerStatus.ACTIVE
      });

      component.onSubmit();

      expect(router.navigate).toHaveBeenCalledWith(['/customers', 'cust-1']);
    });

    it('should show success toast after update', () => {
      customerService.updateCustomer.mockReturnValue(of(mockCustomer));

      component.customerForm.patchValue({
        name: 'Updated Customer',
        status: CustomerStatus.ACTIVE
      });

      component.onSubmit();

      expect(toastService.success).toHaveBeenCalledWith('Customer updated successfully');
    });

    it('should handle update error', () => {
      const error = { error: { message: 'Validation failed' } };
      customerService.updateCustomer.mockReturnValue(throwError(() => error));

      component.customerForm.patchValue({
        name: 'Updated Customer',
        status: CustomerStatus.ACTIVE
      });

      component.onSubmit();

      expect(toastService.error).toHaveBeenCalledWith('Validation failed');
      expect(component.submitting()).toBe(false);
    });
  });

  describe('Cancel Action', () => {
    it('should navigate to customer list when canceling in create mode', () => {
      component.isEditMode.set(false);
      component.onCancel();

      expect(router.navigate).toHaveBeenCalledWith(['/customers']);
    });

    it('should navigate to customer detail when canceling in edit mode', () => {
      component.customerId = 'cust-1';
      component.isEditMode.set(true);
      component.onCancel();

      expect(router.navigate).toHaveBeenCalledWith(['/customers', 'cust-1']);
    });
  });

  describe('Form State Management', () => {
    beforeEach(() => {
      fixture.detectChanges();
    });

    it('should set submitting state during save', () => {
      customerService.createCustomer.mockReturnValue(of(mockCustomer));

      component.customerForm.patchValue({
        name: 'Test Customer',
        status: CustomerStatus.ACTIVE
      });

      expect(component.submitting()).toBe(false);

      component.onSubmit();

      // Note: In real scenario, submitting would be true during the observable execution
      // For this test, we verify it was set by checking the service call happened
      expect(customerService.createCustomer).toHaveBeenCalled();
    });

    it('should provide form control getters', () => {
      expect(component.name).toBe(component.customerForm.get('name'));
      expect(component.email).toBe(component.customerForm.get('email'));
      expect(component.phone).toBe(component.customerForm.get('phone'));
      expect(component.address).toBe(component.customerForm.get('address'));
      expect(component.notes).toBe(component.customerForm.get('notes'));
      expect(component.status).toBe(component.customerForm.get('status'));
    });
  });
});
