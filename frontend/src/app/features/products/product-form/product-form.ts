import { Component, OnInit, inject, DestroyRef, signal } from '@angular/core';
import { RouterModule, Router, ActivatedRoute } from '@angular/router';
import { FormBuilder, FormGroup, Validators, ReactiveFormsModule } from '@angular/forms';
import { HttpContext } from '@angular/common/http';
import { ProductService } from '../../../core/services/product.service';
import { CategoryService } from '../../../core/services/category.service';
import { ToastService } from '../../../core/services/toast.service';
import { SILENT_ERROR } from '../../../core/interceptors/http-context-tokens';
import { ProductStatus, Category, ProductCreateRequest, ProductUpdateRequest } from '../../../models';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { switchMap, finalize } from 'rxjs/operators';
import { of } from 'rxjs';

@Component({
  selector: 'app-product-form',
  standalone: true,
  imports: [RouterModule, ReactiveFormsModule],
  templateUrl: './product-form.html',
  styleUrl: './product-form.scss'
})
export class ProductFormComponent implements OnInit {
  private fb = inject(FormBuilder);
  private productService = inject(ProductService);
  private categoryService = inject(CategoryService);
  private toastService = inject(ToastService);
  private router = inject(Router);
  private route = inject(ActivatedRoute);
  private destroyRef = inject(DestroyRef);

  productForm!: FormGroup;
  categories = signal<Category[]>([]);
  // Create mode is immediately renderable; edit mode sets this while loading its product.
  loading = signal(false);
  submitting = signal(false);
  isEditMode = signal(false);
  productId = signal<string | null>(null);

  ProductStatus = ProductStatus;

  ngOnInit(): void {
    this.initForm();
    this.loadCategories();

    this.route.paramMap
      .pipe(
        switchMap(params => {
          this.productId.set(params.get('id'));
          this.isEditMode.set(!!this.productId());
          if (this.productId()) {
            this.loading.set(true);
            return this.productService.getProduct(this.productId()!)
              .pipe(finalize(() => this.loading.set(false)));
          }

          this.loading.set(false);
          return of(null);
        }),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe({
        next: (product) => {
          if (product) {
            this.productForm.patchValue({
              sku: product.sku,
              name: product.name,
              description: product.description,
              categoryId: product.categoryId || '',
              unitPrice: product.unitPrice,
              unitOfMeasure: product.unitOfMeasure,
              status: product.status
            });
          }
        },
        error: (error) => {
          console.error('Failed to load product:', error);
          this.toastService.error('Failed to load product');
          this.router.navigate(['/products']);
        }
      });
  }

  initForm(): void {
    this.productForm = this.fb.group({
      sku: ['', [Validators.required, Validators.maxLength(100)]],
      name: ['', [Validators.required, Validators.maxLength(200)]],
      description: [''],
      categoryId: [''],
      unitPrice: [0, [Validators.required, Validators.min(0)]],
      unitOfMeasure: ['', [Validators.required, Validators.maxLength(30)]],
      status: [ProductStatus.ACTIVE, Validators.required]
    });
  }

  loadCategories(): void {
    this.categoryService.getCategories()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (categories) => { this.categories.set(categories); },
        error: (error) => {
          console.error('Failed to load categories:', error);
          this.toastService.error('Failed to load categories');
        }
      });
  }

  onSubmit(): void {
    if (this.productForm.invalid) { this.productForm.markAllAsTouched(); return; }

    this.submitting.set(true);
    const formValue = this.productForm.value;

    const request: ProductCreateRequest | ProductUpdateRequest = {
      sku: formValue.sku!,
      name: formValue.name!,
      description: formValue.description || undefined,
      categoryId: formValue.categoryId || undefined,
      unitPrice: formValue.unitPrice!,
      unitOfMeasure: formValue.unitOfMeasure || undefined,
      status: formValue.status
    };

    const productId = this.productId();
    const silentCtx = new HttpContext().set(SILENT_ERROR, true);
    const operation = this.isEditMode() && productId
      ? this.productService.updateProduct(productId, request as ProductUpdateRequest, silentCtx)
      : this.productService.createProduct(request as ProductCreateRequest, silentCtx);

    operation
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (product) => {
          this.toastService.success(this.isEditMode() ? 'Product updated successfully' : 'Product created successfully');
          this.router.navigate(['/products', product.id]);
        },
        error: (error) => {
          console.error('Failed to save product:', error);
          this.toastService.error(error.error?.message || 'Failed to save product');
          this.submitting.set(false);
        }
      });
  }

  onCancel(): void {
    const productId = this.productId();
    if (this.isEditMode() && productId) {
      this.router.navigate(['/products', productId]);
    } else {
      this.router.navigate(['/products']);
    }
  }

  get sku()           { return this.productForm.get('sku'); }
  get name()          { return this.productForm.get('name'); }
  get description()   { return this.productForm.get('description'); }
  get categoryId()    { return this.productForm.get('categoryId'); }
  get unitPrice()     { return this.productForm.get('unitPrice'); }
  get unitOfMeasure() { return this.productForm.get('unitOfMeasure'); }
  get status()        { return this.productForm.get('status'); }
}

