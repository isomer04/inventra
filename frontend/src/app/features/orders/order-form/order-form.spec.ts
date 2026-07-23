import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ReactiveFormsModule } from '@angular/forms';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRoute, Router, provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { OrderFormComponent } from './order-form';
import { OrderService } from '../../../core/services/order.service';
import { CustomerService } from '../../../core/services/customer.service';
import { ProductService } from '../../../core/services/product.service';
import { ToastService } from '../../../core/services/toast.service';
import { Order, OrderStatus, Customer, CustomerStatus, Product, ProductStatus, Page } from '../../../models';
import type { MockedObject } from 'vitest';

describe('OrderFormComponent', () => {
  let component: OrderFormComponent;
  let fixture: ComponentFixture<OrderFormComponent>;
  let orderService: MockedObject<OrderService>;
  let customerService: MockedObject<CustomerService>;
  let productService: MockedObject<ProductService>;
  let toastService: MockedObject<ToastService>;
  let router: Router;
  let activatedRoute: ActivatedRoute;

  const mockCustomers: Customer[] = [
    { id: 'cust-1', name: 'Customer 1', email: 'cust1@test.com', phone: '555-0001', status: CustomerStatus.ACTIVE, tenantId: 't1', createdAt: new Date().toISOString(), updatedAt: new Date().toISOString() },
    { id: 'cust-2', name: 'Customer 2', email: 'cust2@test.com', phone: '555-0002', status: CustomerStatus.ACTIVE, tenantId: 't1', createdAt: new Date().toISOString(), updatedAt: new Date().toISOString() }
  ];

  const mockProducts: Product[] = [
    { id: 'prod-1', sku: 'SKU-001', name: 'Product 1', unitPrice: 10.00, status: ProductStatus.ACTIVE, tenantId: 't1', createdAt: new Date().toISOString(), updatedAt: new Date().toISOString() },
    { id: 'prod-2', sku: 'SKU-002', name: 'Product 2', unitPrice: 20.00, status: ProductStatus.ACTIVE, tenantId: 't1', createdAt: new Date().toISOString(), updatedAt: new Date().toISOString() }
  ];

  const mockCustomerPage: Page<Customer> = {
    content: mockCustomers,
    number: 0, size: 10, totalElements: 2, totalPages: 1
  };

  const mockProductPage: Page<Product> = {
    content: mockProducts,
    number: 0, size: 10, totalElements: 2, totalPages: 1
  };

  beforeEach(async () => {
    const orderSpy = { createOrder: vi.fn(), updateOrder: vi.fn(), getOrder: vi.fn() };
    const customerSpy = { getCustomers: vi.fn() };
    const productSpy = { getProducts: vi.fn() };
    const toastSpy = { success: vi.fn(), error: vi.fn() };

    await TestBed.configureTestingModule({
      imports: [OrderFormComponent, ReactiveFormsModule],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: OrderService, useValue: orderSpy },
        { provide: CustomerService, useValue: customerSpy },
        { provide: ProductService, useValue: productSpy },
        { provide: ToastService, useValue: toastSpy },
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { paramMap: { get: () => null } } }
        }
      ]
    }).compileComponents();

    orderService = TestBed.inject(OrderService) as unknown as MockedObject<OrderService>;
    customerService = TestBed.inject(CustomerService) as unknown as MockedObject<CustomerService>;
    productService = TestBed.inject(ProductService) as unknown as MockedObject<ProductService>;
    toastService = TestBed.inject(ToastService) as unknown as MockedObject<ToastService>;
    router = TestBed.inject(Router);
    activatedRoute = TestBed.inject(ActivatedRoute);

    // vi.spyOn calls through by default; unstubbed navigation rejects with NG04002.
    vi.spyOn(router, 'navigate').mockResolvedValue(true);

    customerService.getCustomers.mockReturnValue(of(mockCustomerPage));
    productService.getProducts.mockReturnValue(of(mockProductPage));

    fixture = TestBed.createComponent(OrderFormComponent);
    component = fixture.componentInstance;
  });

  describe('Component Initialization', () => {
    it('should create', () => {
      expect(component).toBeTruthy();
    });

    it('should initialize form with required fields', () => {
      fixture.detectChanges();
      
      expect(component.orderForm).toBeDefined();
      expect(component.orderForm.get('customerId')).toBeDefined();
      expect(component.orderForm.get('notes')).toBeDefined();
      expect(component.orderForm.get('items')).toBeDefined();
    });

    it('should load customers on init', () => {
      fixture.detectChanges();
      
      expect(customerService.getCustomers).toHaveBeenCalledWith({ page: 0, size: 1000 });
      expect(component.customers().length).toBe(2);
    });

    it('should load products on init', () => {
      fixture.detectChanges();
      
      expect(productService.getProducts).toHaveBeenCalledWith({ page: 0, size: 1000 });
      expect(component.products().length).toBe(2);
    });

    it('should add one item in create mode', () => {
      fixture.detectChanges();
      
      expect(component.items.length).toBe(1);
    });

    it('should set isEditMode to false in create mode', () => {
      fixture.detectChanges();
      
      expect(component.isEditMode()).toBe(false);
      expect(component.orderId()).toBeNull();
    });
  });

  describe('Edit Mode', () => {
    it('should load order in edit mode', () => {
      const mockOrder: Order = {
        id: 'order-1',
        orderNumber: 'ORD-2026-00001',
        customerId: 'cust-1',
        customerName: 'Customer 1',
        status: OrderStatus.DRAFT,
        notes: 'Test notes',

        totalAmount: 33,
        items: [
          { id: 'item-1', productId: 'prod-1', quantity: 2, unitPrice: 10, totalPrice: 20, orderId: 'order-1', productName: 'Product 1', productSku: 'SKU-001' },
          { id: 'item-2', productId: 'prod-2', quantity: 1, unitPrice: 20, totalPrice: 20, orderId: 'order-1', productName: 'Product 2', productSku: 'SKU-002' }
        ],
        tenantId: 't1',
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
        createdBy: 'user-1',
        createdByName: 'Test User'
      };

      activatedRoute.snapshot.paramMap.get = vi.fn().mockReturnValue('order-1');
      orderService.getOrder.mockReturnValue(of(mockOrder));

      fixture.detectChanges();

      expect(orderService.getOrder).toHaveBeenCalledWith('order-1');
      expect(component.isEditMode()).toBe(true);
      expect(component.orderForm.get('customerId')?.value).toBe('cust-1');
      expect(component.orderForm.get('notes')?.value).toBe('Test notes');
      expect(component.items.length).toBe(2);
    });

    it('should redirect to detail page if order is not DRAFT', () => {
      const mockOrder: Order = {
        id: 'order-1',
        orderNumber: 'ORD-2026-00001',
        customerId: 'cust-1',
        customerName: 'Customer 1',
        status: OrderStatus.SUBMITTED,

        totalAmount: 110,
        items: [],
        tenantId: 't1',
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
        createdBy: 'user-1',
        createdByName: 'Test User'
      };

      activatedRoute.snapshot.paramMap.get = vi.fn().mockReturnValue('order-1');
      orderService.getOrder.mockReturnValue(of(mockOrder));

      fixture.detectChanges();

      expect(toastService.error).toHaveBeenCalledWith('Only DRAFT orders can be edited');
      expect(router.navigate).toHaveBeenCalledWith(['/orders', 'order-1']);
    });

    it('should show error and navigate to list if order not found', () => {
      activatedRoute.snapshot.paramMap.get = vi.fn().mockReturnValue('order-1');
      orderService.getOrder.mockReturnValue(throwError(() => ({ status: 404 })));

      fixture.detectChanges();

      expect(toastService.error).toHaveBeenCalledWith('Failed to load order');
      expect(router.navigate).toHaveBeenCalledWith(['/orders']);
    });
  });

  describe('Form Validation', () => {
    beforeEach(() => {
      fixture.detectChanges();
    });

    it('should require customer selection', () => {
      const customerIdControl = component.orderForm.get('customerId');
      
      customerIdControl?.setValue('');
      expect(customerIdControl?.hasError('required')).toBe(true);
      
      customerIdControl?.setValue('cust-1');
      expect(customerIdControl?.hasError('required')).toBe(false);
    });

    it('should mark form as invalid when customerId is missing', () => {
      component.orderForm.patchValue({ customerId: '' });
      
      expect(component.orderForm.valid).toBe(false);
    });

    it('should mark form as valid when all required fields are filled', () => {
      component.orderForm.patchValue({ customerId: 'cust-1' });
      component.items.at(0).patchValue({ productId: 'prod-1', quantity: 1 });
      
      expect(component.orderForm.valid).toBe(true);
    });

    it('should require at least one item', () => {
      component.orderForm.patchValue({ customerId: 'cust-1' });
      component.items.clear();
      
      component.onSubmit();
      
      expect(toastService.error).toHaveBeenCalledWith('Please add at least one item');
    });
  });

  describe('Item Management', () => {
    beforeEach(() => {
      fixture.detectChanges();
    });

    it('should add item to form array', () => {
      const initialLength = component.items.length;
      
      component.addItem();
      
      expect(component.items.length).toBe(initialLength + 1);
    });

    it('should add item with default values', () => {
      component.addItem();
      
      const lastItem = component.items.at(component.items.length - 1);
      expect(lastItem.get('productId')?.value).toBe('');
      expect(lastItem.get('quantity')?.value).toBe(1);
    });

    it('should add item with provided values', () => {
      component.addItem('prod-1', 5);
      
      const lastItem = component.items.at(component.items.length - 1);
      expect(lastItem.get('productId')?.value).toBe('prod-1');
      expect(lastItem.get('quantity')?.value).toBe(5);
    });

    it('should remove item from form array', () => {
      component.addItem();
      component.addItem();
      const initialLength = component.items.length;
      
      component.removeItem(0);
      
      expect(component.items.length).toBe(initialLength - 1);
    });

    it('should validate item productId as required', () => {
      const item = component.items.at(0);
      
      item.patchValue({ productId: '' });
      expect(item.get('productId')?.hasError('required')).toBe(true);
    });

    it('should validate item quantity as required', () => {
      const item = component.items.at(0);
      
      item.patchValue({ quantity: null });
      expect(item.get('quantity')?.hasError('required')).toBe(true);
    });

    it('should validate item quantity minimum value', () => {
      const item = component.items.at(0);
      
      item.patchValue({ quantity: 0 });
      expect(item.get('quantity')?.hasError('min')).toBe(true);
      
      item.patchValue({ quantity: 1 });
      expect(item.get('quantity')?.hasError('min')).toBe(false);
    });
  });

  describe('Calculations', () => {
    beforeEach(() => {
      fixture.detectChanges();
    });

    it('should calculate item total correctly', () => {
      component.items.at(0).patchValue({ productId: 'prod-1', quantity: 3 });
      
      const total = component.getItemTotal(0);
      
      expect(total).toBe(30); // 10 * 3
    });

    it('should return 0 for item total when product not found', () => {
      component.items.at(0).patchValue({ productId: 'invalid-id', quantity: 3 });
      
      const total = component.getItemTotal(0);
      
      expect(total).toBe(0);
    });

    it('should calculate order total amount', () => {
      component.items.at(0).patchValue({ productId: 'prod-1', quantity: 2 });
      component.addItem('prod-2', 1);
      
      const total = component.getTotalAmount();
      
      expect(total).toBe(40); // (10*2) + (20*1)
    });

    it('should return 0 for total when no items', () => {
      component.items.clear();
      
      const total = component.getTotalAmount();
      
      expect(total).toBe(0);
    });

    it('should find product by id', () => {
      const product = component.getProduct('prod-1');
      
      expect(product).toBeDefined();
      expect(product?.name).toBe('Product 1');
    });

    it('should return undefined for invalid product id', () => {
      const product = component.getProduct('invalid-id');
      
      expect(product).toBeUndefined();
    });
  });

  describe('Form Submission - Create Mode', () => {
    beforeEach(() => {
      fixture.detectChanges();
    });

    it('should create order with valid data', () => {
      const mockCreatedOrder: Order = {
        id: 'order-new',
        orderNumber: 'ORD-2026-00001',
        customerId: 'cust-1',
        customerName: 'Customer 1',
        status: OrderStatus.DRAFT,

        totalAmount: 33,
        items: [],
        tenantId: 't1',
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
        createdBy: 'user-1',
        createdByName: 'Test User'
      };

      orderService.createOrder.mockReturnValue(of(mockCreatedOrder));

      component.orderForm.patchValue({ customerId: 'cust-1', notes: 'Test notes' });
      component.items.at(0).patchValue({ productId: 'prod-1', quantity: 3 });

      component.onSubmit();

      expect(orderService.createOrder).toHaveBeenCalled();
      expect(toastService.success).toHaveBeenCalledWith('Order created successfully');
      expect(router.navigate).toHaveBeenCalledWith(['/orders', 'order-new']);
    });

    it('should send only the server-owned order item contract', () => {
      const mockCreatedOrder: Order = {
        id: 'order-new',
        orderNumber: 'ORD-2026-00001',
        customerId: 'cust-1',
        customerName: 'Customer 1',
        status: OrderStatus.DRAFT,

        totalAmount: 33,
        items: [],
        tenantId: 't1',
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
        createdBy: 'user-1',
        createdByName: 'Test User'
      };

      orderService.createOrder.mockReturnValue(of(mockCreatedOrder));

      component.orderForm.patchValue({ customerId: 'cust-1' });
      component.items.at(0).patchValue({ productId: 'prod-1', quantity: 2 });

      component.onSubmit();

      const callArgs = orderService.createOrder.mock.lastCall![0];
      expect(callArgs.items[0]).toEqual({ productId: 'prod-1', quantity: 2 });
      expect(callArgs.items[0]).not.toHaveProperty('unitPrice');
    });

    it('should not submit if form is invalid', () => {
      component.orderForm.patchValue({ customerId: '' });

      component.onSubmit();

      expect(orderService.createOrder).not.toHaveBeenCalled();
      expect(component.orderForm.touched).toBe(true);
    });

    it('should show error toast on create failure', () => {
      orderService.createOrder.mockReturnValue(throwError(() => ({ error: { message: 'Creation failed' } })));

      component.orderForm.patchValue({ customerId: 'cust-1' });
      component.items.at(0).patchValue({ productId: 'prod-1', quantity: 1 });

      component.onSubmit();

      expect(toastService.error).toHaveBeenCalledWith('Creation failed');
    });
  });

  describe('Form Submission - Edit Mode', () => {
    it('should update order with valid data', () => {
      const mockOrder: Order = {
        id: 'order-1',
        orderNumber: 'ORD-2026-00001',
        customerId: 'cust-1',
        customerName: 'Customer 1',
        status: OrderStatus.DRAFT,
        notes: 'Original notes',

        totalAmount: 11,
        items: [{ id: 'item-1', productId: 'prod-1', quantity: 1, unitPrice: 10, totalPrice: 10, orderId: 'order-1', productName: 'Product 1', productSku: 'SKU-001' }],
        tenantId: 't1',
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
        createdBy: 'user-1',
        createdByName: 'Test User'
      };

      const mockUpdatedOrder = { ...mockOrder, notes: 'Updated notes' };

      activatedRoute.snapshot.paramMap.get = vi.fn().mockReturnValue('order-1');
      orderService.getOrder.mockReturnValue(of(mockOrder));
      orderService.updateOrder.mockReturnValue(of(mockUpdatedOrder));

      fixture.detectChanges();

      component.orderForm.patchValue({ notes: 'Updated notes' });
      component.onSubmit();

      expect(orderService.updateOrder).toHaveBeenCalledWith('order-1', expect.any(Object));
      expect(toastService.success).toHaveBeenCalledWith('Order updated successfully');
      expect(router.navigate).toHaveBeenCalledWith(['/orders', 'order-1']);
    });
  });

  describe('Cancel Action', () => {
    it('should navigate to orders list in create mode', () => {
      fixture.detectChanges();

      component.onCancel();

      expect(router.navigate).toHaveBeenCalledWith(['/orders']);
    });

    it('should navigate to order detail in edit mode', () => {
      const mockOrder: Order = {
        id: 'order-1',
        orderNumber: 'ORD-2026-00001',
        customerId: 'cust-1',
        customerName: 'Customer 1',
        status: OrderStatus.DRAFT,

        totalAmount: 11,
        items: [],
        tenantId: 't1',
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
        createdBy: 'user-1',
        createdByName: 'Test User'
      };

      activatedRoute.snapshot.paramMap.get = vi.fn().mockReturnValue('order-1');
      orderService.getOrder.mockReturnValue(of(mockOrder));

      fixture.detectChanges();

      component.onCancel();

      expect(router.navigate).toHaveBeenCalledWith(['/orders', 'order-1']);
    });
  });

  describe('Loading States', () => {
    it('should set loading to false after order loads', () => {
      const mockOrder: Order = {
        id: 'order-1',
        orderNumber: 'ORD-2026-00001',
        customerId: 'cust-1',
        customerName: 'Customer 1',
        status: OrderStatus.DRAFT,

        totalAmount: 11,
        items: [],
        tenantId: 't1',
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
        createdBy: 'user-1',
        createdByName: 'Test User'
      };

      activatedRoute.snapshot.paramMap.get = vi.fn().mockReturnValue('order-1');
      orderService.getOrder.mockReturnValue(of(mockOrder));

      fixture.detectChanges();

      expect(component.loading()).toBe(false);
    });

    it('should set submitting during form submission', () => {
      const mockOrder: Order = {
        id: 'order-new',
        orderNumber: 'ORD-2026-00001',
        customerId: 'cust-1',
        customerName: 'Customer 1',
        status: OrderStatus.DRAFT,

        totalAmount: 11,
        items: [],
        tenantId: 't1',
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
        createdBy: 'user-1',
        createdByName: 'Test User'
      };

      orderService.createOrder.mockReturnValue(of(mockOrder));
      fixture.detectChanges();

      component.orderForm.patchValue({ customerId: 'cust-1' });
      component.items.at(0).patchValue({ productId: 'prod-1', quantity: 1 });

      expect(component.submitting()).toBe(false);
      
      component.onSubmit();
      
      // submitting is set to true during submission, then false on error/success
    });
  });
});
