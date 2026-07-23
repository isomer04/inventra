import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ProductFormComponent } from './product-form';
import { ProductService } from '../../../core/services/product.service';
import { CategoryService } from '../../../core/services/category.service';
import { ToastService } from '../../../core/services/toast.service';
import { Router } from '@angular/router';
import { ActivatedRoute } from '@angular/router';
import { of, Subject, throwError } from 'rxjs';

import { ProductStatus } from '../../../models';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import type { MockedObject } from 'vitest';

describe('ProductFormComponent', () => {
  let component: ProductFormComponent;
  let fixture: ComponentFixture<ProductFormComponent>;
  let productService: MockedObject<ProductService>;
  let categoryService: MockedObject<CategoryService>;
  let toastService: MockedObject<ToastService>;
  let router: MockedObject<Router>;

  const mockCategories = [
    { id: 'cat-1', name: 'Electronics', description: 'Electronic items', tenantId: 'tenant-1', createdAt: '2024-01-01', updatedAt: '2024-01-01' },
    { id: 'cat-2', name: 'Furniture', description: 'Office furniture', tenantId: 'tenant-1', createdAt: '2024-01-01', updatedAt: '2024-01-01' }
  ];

  const mockProduct = {
    id: 'prod-1',
    tenantId: 'tenant-1',
    sku: 'LAPTOP-001',
    name: 'Business Laptop',
    description: 'High-performance laptop',
    categoryId: 'cat-1',
    unitPrice: 999.99,
    unitOfMeasure: 'EA',
    status: ProductStatus.ACTIVE,
    createdAt: '2024-01-15T10:00:00Z',
    updatedAt: '2024-01-15T10:00:00Z'
  };

  beforeEach(async () => {
    const productServiceSpy = { getProduct: vi.fn(), createProduct: vi.fn(), updateProduct: vi.fn() };
    const categoryServiceSpy = { getCategories: vi.fn() };
    const toastServiceSpy = { success: vi.fn(), error: vi.fn() };
    const routerSpy = { navigate: vi.fn() };

    await TestBed.configureTestingModule({
      imports: [ProductFormComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: ProductService, useValue: productServiceSpy },
        { provide: CategoryService, useValue: categoryServiceSpy },
        { provide: ToastService, useValue: toastServiceSpy },
        { provide: Router, useValue: routerSpy },
        {
          provide: ActivatedRoute,
          useValue: { paramMap: of(new Map()) }
        }
      ]
    }).compileComponents();

    productService = TestBed.inject(ProductService) as unknown as MockedObject<ProductService>;
    categoryService = TestBed.inject(CategoryService) as unknown as MockedObject<CategoryService>;
    toastService = TestBed.inject(ToastService) as unknown as MockedObject<ToastService>;
    router = TestBed.inject(Router) as unknown as MockedObject<Router>;

    fixture = TestBed.createComponent(ProductFormComponent);
    component = fixture.componentInstance;
  });

  describe('Component Initialization', () => {
    it('should create', () => {
      expect(component).toBeTruthy();
    });

    it('should load categories on init', () => {
      categoryService.getCategories.mockReturnValue(of(mockCategories));

      fixture.detectChanges();

      expect(categoryService.getCategories).toHaveBeenCalled();
      expect(component.categories()).toEqual(mockCategories);
    });

    it('should initialize form with default values in create mode', () => {
      categoryService.getCategories.mockReturnValue(of(mockCategories));

      fixture.detectChanges();

      expect(component.isEditMode()).toBe(false);
      expect(component.productForm.value).toEqual({
        sku: '',
        name: '',
        description: '',
        categoryId: '',
        unitPrice: 0,
        unitOfMeasure: '',
        status: ProductStatus.ACTIVE
      });
    });

    it('should handle category load error', () => {
      const error = { message: 'Failed to fetch' };
      categoryService.getCategories.mockReturnValue(throwError(() => error));

      fixture.detectChanges();

      expect(toastService.error).toHaveBeenCalledWith('Failed to load categories');
    });
  });

  describe('Edit Mode - Load Product', () => {
    it('should load product data in edit mode', () => {
      categoryService.getCategories.mockReturnValue(of(mockCategories));
      productService.getProduct.mockReturnValue(of(mockProduct));

      const route = TestBed.inject(ActivatedRoute);
      (route.paramMap as any) = of(new Map([['id', 'prod-1']]));

      fixture = TestBed.createComponent(ProductFormComponent);
      component = fixture.componentInstance;
      fixture.detectChanges();

      expect(productService.getProduct).toHaveBeenCalledWith('prod-1');
      expect(component.isEditMode()).toBe(true);
      expect(component.productId()).toBe('prod-1');
      expect(component.productForm.value).toEqual({
        sku: mockProduct.sku,
        name: mockProduct.name,
        description: mockProduct.description,
        categoryId: mockProduct.categoryId,
        unitPrice: mockProduct.unitPrice,
        unitOfMeasure: mockProduct.unitOfMeasure,
        status: mockProduct.status
      });
    });

    it('should set loading state while fetching product', () => {
      categoryService.getCategories.mockReturnValue(of(mockCategories));
      const productRequest = new Subject<typeof mockProduct>();
      productService.getProduct.mockReturnValue(productRequest);

      const route = TestBed.inject(ActivatedRoute);
      (route.paramMap as any) = of(new Map([['id', 'prod-1']]));

      fixture = TestBed.createComponent(ProductFormComponent);
      component = fixture.componentInstance;
      fixture.detectChanges();

      expect(component.loading()).toBe(true);

      productRequest.complete();

      expect(component.loading()).toBe(false);
    });

    it('should handle product load error and navigate to list', () => {
      categoryService.getCategories.mockReturnValue(of(mockCategories));
      const error = { message: 'Product not found' };
      productService.getProduct.mockReturnValue(throwError(() => error));

      const route = TestBed.inject(ActivatedRoute);
      (route.paramMap as any) = of(new Map([['id', 'prod-1']]));

      fixture = TestBed.createComponent(ProductFormComponent);
      component = fixture.componentInstance;
      fixture.detectChanges();

      expect(toastService.error).toHaveBeenCalledWith('Failed to load product');
      expect(router.navigate).toHaveBeenCalledWith(['/products']);
    });
  });

  describe('Form Validation', () => {
    beforeEach(() => {
      categoryService.getCategories.mockReturnValue(of(mockCategories));
      fixture.detectChanges();
    });

    it('should validate SKU as required', () => {
      const skuControl = component.sku!;
      skuControl.setValue('');

      expect(skuControl.hasError('required')).toBe(true);
      expect(component.productForm.invalid).toBe(true);
    });

    it('should validate SKU max length (100 chars)', () => {
      const skuControl = component.sku!;
      const longSku = 'A'.repeat(101);
      skuControl.setValue(longSku);

      expect(skuControl.hasError('maxlength')).toBe(true);
    });

    it('should accept valid SKU', () => {
      const skuControl = component.sku!;
      skuControl.setValue('LAPTOP-001');

      expect(skuControl.valid).toBe(true);
    });

    it('should validate name as required', () => {
      const nameControl = component.name!;
      nameControl.setValue('');

      expect(nameControl.hasError('required')).toBe(true);
    });

    it('should validate name max length (200 chars)', () => {
      const nameControl = component.name!;
      const longName = 'A'.repeat(201);
      nameControl.setValue(longName);

      expect(nameControl.hasError('maxlength')).toBe(true);
    });

    it('should accept valid name', () => {
      const nameControl = component.name!;
      nameControl.setValue('Business Laptop');

      expect(nameControl.valid).toBe(true);
    });

    it('should allow empty description (optional field)', () => {
      const descControl = component.description!;
      descControl.setValue('');

      expect(descControl.valid).toBe(true);
    });

    it('should allow empty categoryId (optional field)', () => {
      const categoryControl = component.categoryId!;
      categoryControl.setValue('');

      expect(categoryControl.valid).toBe(true);
    });

    it('should validate unitPrice as required', () => {
      const priceControl = component.unitPrice!;
      priceControl.setValue(null);

      expect(priceControl.hasError('required')).toBe(true);
    });

    it('should validate unitPrice minimum value (0)', () => {
      const priceControl = component.unitPrice!;
      priceControl.setValue(-1);

      expect(priceControl.hasError('min')).toBe(true);
    });

    it('should accept valid unitPrice', () => {
      const priceControl = component.unitPrice!;
      priceControl.setValue(99.99);

      expect(priceControl.valid).toBe(true);
    });

    it('should accept zero unitPrice', () => {
      const priceControl = component.unitPrice!;
      priceControl.setValue(0);

      expect(priceControl.valid).toBe(true);
    });

    it('should validate unitOfMeasure as required', () => {
      const uomControl = component.unitOfMeasure!;
      uomControl.setValue('');

      expect(uomControl.hasError('required')).toBe(true);
    });

    it('should validate unitOfMeasure max length (30 chars)', () => {
      const uomControl = component.unitOfMeasure!;
      const longUom = 'A'.repeat(31);
      uomControl.setValue(longUom);

      expect(uomControl.hasError('maxlength')).toBe(true);
    });

    it('should accept valid unitOfMeasure', () => {
      const uomControl = component.unitOfMeasure!;
      uomControl.setValue('EA');

      expect(uomControl.valid).toBe(true);
    });

    it('should validate status as required', () => {
      const statusControl = component.status!;
      statusControl.setValue(null);

      expect(statusControl.hasError('required')).toBe(true);
    });

    it('should accept valid status values', () => {
      const statusControl = component.status!;

      statusControl.setValue(ProductStatus.ACTIVE);
      expect(statusControl.valid).toBe(true);

      statusControl.setValue(ProductStatus.DISCONTINUED);
      expect(statusControl.valid).toBe(true);
    });
  });

  describe('Form Submission - Create Mode', () => {
    beforeEach(() => {
      categoryService.getCategories.mockReturnValue(of(mockCategories));
      fixture.detectChanges();
    });

    it('should not submit when form is invalid', () => {
      component.productForm.patchValue({ sku: '', name: '' });
      component.onSubmit();

      expect(productService.createProduct).not.toHaveBeenCalled();
    });

    it('should mark all fields as touched when submitting invalid form', () => {
      component.productForm.patchValue({ sku: '', name: '' });
      component.onSubmit();

      expect(component.sku?.touched).toBe(true);
      expect(component.name?.touched).toBe(true);
    });

    it('should create product with valid data', () => {
      const newProduct = { ...mockProduct, id: 'new-prod-1' };
      productService.createProduct.mockReturnValue(of(newProduct));

      component.productForm.patchValue({
        sku: 'DESK-001',
        name: 'Office Desk',
        description: 'Large office desk',
        categoryId: 'cat-2',
        unitPrice: 299.99,
        unitOfMeasure: 'EA',
        status: ProductStatus.ACTIVE
      });

      component.onSubmit();

      expect(productService.createProduct).toHaveBeenCalledWith(
        {
          sku: 'DESK-001',
          name: 'Office Desk',
          description: 'Large office desk',
          categoryId: 'cat-2',
          unitPrice: 299.99,
          unitOfMeasure: 'EA',
          status: ProductStatus.ACTIVE
        },
        expect.any(Object) // HttpContext
      );
    });

    it('should navigate to product detail after successful creation', () => {
      const newProduct = { ...mockProduct, id: 'new-prod-1' };
      productService.createProduct.mockReturnValue(of(newProduct));

      component.productForm.patchValue({
        sku: 'TEST-001',
        name: 'Test Product',
        unitPrice: 10.00,
        unitOfMeasure: 'EA',
        status: ProductStatus.ACTIVE
      });

      component.onSubmit();

      expect(router.navigate).toHaveBeenCalledWith(['/products', 'new-prod-1']);
    });

    it('should show success toast after creation', () => {
      const newProduct = { ...mockProduct, id: 'new-prod-1' };
      productService.createProduct.mockReturnValue(of(newProduct));

      component.productForm.patchValue({
        sku: 'TEST-001',
        name: 'Test Product',
        unitPrice: 10.00,
        unitOfMeasure: 'EA',
        status: ProductStatus.ACTIVE
      });

      component.onSubmit();

      expect(toastService.success).toHaveBeenCalledWith('Product created successfully');
    });

    it('should handle creation error with custom message', () => {
      const error = { error: { message: 'SKU already exists' } };
      productService.createProduct.mockReturnValue(throwError(() => error));

      component.productForm.patchValue({
        sku: 'DUPLICATE-001',
        name: 'Test Product',
        unitPrice: 10.00,
        unitOfMeasure: 'EA',
        status: ProductStatus.ACTIVE
      });

      component.onSubmit();

      expect(toastService.error).toHaveBeenCalledWith('SKU already exists');
      expect(component.submitting()).toBe(false);
    });

    it('should handle creation error with default message', () => {
      const error = { error: {} };
      productService.createProduct.mockReturnValue(throwError(() => error));

      component.productForm.patchValue({
        sku: 'TEST-001',
        name: 'Test Product',
        unitPrice: 10.00,
        unitOfMeasure: 'EA',
        status: ProductStatus.ACTIVE
      });

      component.onSubmit();

      expect(toastService.error).toHaveBeenCalledWith('Failed to save product');
    });

    it('should omit empty optional fields when creating', () => {
      const newProduct = { ...mockProduct, id: 'new-prod-1' };
      productService.createProduct.mockReturnValue(of(newProduct));

      component.productForm.patchValue({
        sku: 'MINIMAL-001',
        name: 'Minimal Product',
        description: '',
        categoryId: '',
        unitPrice: 5.00,
        unitOfMeasure: 'EA',
        status: ProductStatus.ACTIVE
      });

      component.onSubmit();

      expect(productService.createProduct).toHaveBeenCalledWith(
        {
          sku: 'MINIMAL-001',
          name: 'Minimal Product',
          description: undefined,
          categoryId: undefined,
          unitPrice: 5.00,
          unitOfMeasure: 'EA',
          status: ProductStatus.ACTIVE
        },
        expect.any(Object)
      );
    });
  });

  describe('Form Submission - Edit Mode', () => {
    beforeEach(() => {
      categoryService.getCategories.mockReturnValue(of(mockCategories));
      productService.getProduct.mockReturnValue(of(mockProduct));

      const route = TestBed.inject(ActivatedRoute);
      (route.paramMap as any) = of(new Map([['id', 'prod-1']]));

      fixture = TestBed.createComponent(ProductFormComponent);
      component = fixture.componentInstance;
      fixture.detectChanges();
    });

    it('should update product with valid data', () => {
      productService.updateProduct.mockReturnValue(of(mockProduct));

      component.productForm.patchValue({
        sku: 'UPDATED-001',
        name: 'Updated Product',
        description: 'Updated description',
        categoryId: 'cat-2',
        unitPrice: 199.99,
        unitOfMeasure: 'BOX',
        status: ProductStatus.DISCONTINUED
      });

      component.onSubmit();

      expect(productService.updateProduct).toHaveBeenCalledWith(
        'prod-1',
        {
          sku: 'UPDATED-001',
          name: 'Updated Product',
          description: 'Updated description',
          categoryId: 'cat-2',
          unitPrice: 199.99,
          unitOfMeasure: 'BOX',
          status: ProductStatus.DISCONTINUED
        },
        expect.any(Object)
      );
    });

    it('should navigate to product detail after successful update', () => {
      productService.updateProduct.mockReturnValue(of(mockProduct));

      component.productForm.patchValue({
        sku: 'UPDATED-001',
        name: 'Updated Product',
        unitPrice: 99.99,
        unitOfMeasure: 'EA',
        status: ProductStatus.ACTIVE
      });

      component.onSubmit();

      expect(router.navigate).toHaveBeenCalledWith(['/products', 'prod-1']);
    });

    it('should show success toast after update', () => {
      productService.updateProduct.mockReturnValue(of(mockProduct));

      component.productForm.patchValue({
        sku: 'UPDATED-001',
        name: 'Updated Product',
        unitPrice: 99.99,
        unitOfMeasure: 'EA',
        status: ProductStatus.ACTIVE
      });

      component.onSubmit();

      expect(toastService.success).toHaveBeenCalledWith('Product updated successfully');
    });

    it('should handle update error', () => {
      const error = { error: { message: 'Validation failed' } };
      productService.updateProduct.mockReturnValue(throwError(() => error));

      component.productForm.patchValue({
        sku: 'UPDATED-001',
        name: 'Updated Product',
        unitPrice: 99.99,
        unitOfMeasure: 'EA',
        status: ProductStatus.ACTIVE
      });

      component.onSubmit();

      expect(toastService.error).toHaveBeenCalledWith('Validation failed');
      expect(component.submitting()).toBe(false);
    });
  });

  describe('Cancel Action', () => {
    beforeEach(() => {
      categoryService.getCategories.mockReturnValue(of(mockCategories));
    });

    it('should navigate to product list when canceling in create mode', () => {
      fixture.detectChanges();

      component.isEditMode.set(false);
      component.productId.set(null);
      component.onCancel();

      expect(router.navigate).toHaveBeenCalledWith(['/products']);
    });

    it('should navigate to product detail when canceling in edit mode', () => {
      productService.getProduct.mockReturnValue(of(mockProduct));

      const route = TestBed.inject(ActivatedRoute);
      (route.paramMap as any) = of(new Map([['id', 'prod-1']]));

      fixture = TestBed.createComponent(ProductFormComponent);
      component = fixture.componentInstance;
      fixture.detectChanges();

      component.onCancel();

      expect(router.navigate).toHaveBeenCalledWith(['/products', 'prod-1']);
    });
  });

  describe('Form State Management', () => {
    beforeEach(() => {
      categoryService.getCategories.mockReturnValue(of(mockCategories));
      fixture.detectChanges();
    });

    it('should set submitting state during save', () => {
      productService.createProduct.mockReturnValue(of(mockProduct));

      component.productForm.patchValue({
        sku: 'TEST-001',
        name: 'Test Product',
        unitPrice: 10.00,
        unitOfMeasure: 'EA',
        status: ProductStatus.ACTIVE
      });

      expect(component.submitting()).toBe(false);

      component.onSubmit();

      expect(productService.createProduct).toHaveBeenCalled();
    });

    it('should provide form control getters', () => {
      expect(component.sku).toBe(component.productForm.get('sku'));
      expect(component.name).toBe(component.productForm.get('name'));
      expect(component.description).toBe(component.productForm.get('description'));
      expect(component.categoryId).toBe(component.productForm.get('categoryId'));
      expect(component.unitPrice).toBe(component.productForm.get('unitPrice'));
      expect(component.unitOfMeasure).toBe(component.productForm.get('unitOfMeasure'));
      expect(component.status).toBe(component.productForm.get('status'));
    });
  });

  describe('Categories Signal', () => {
    it('should initialize with empty array', () => {
      expect(component.categories()).toEqual([]);
    });

    it('should populate categories signal on successful load', () => {
      categoryService.getCategories.mockReturnValue(of(mockCategories));

      fixture.detectChanges();

      expect(component.categories()).toEqual(mockCategories);
    });

    it('should keep empty array on category load failure', () => {
      categoryService.getCategories.mockReturnValue(throwError(() => new Error('Failed')));

      fixture.detectChanges();

      expect(component.categories()).toEqual([]);
    });
  });
});
