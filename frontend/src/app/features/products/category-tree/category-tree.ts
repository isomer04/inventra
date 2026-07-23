import { Component, OnInit, inject, DestroyRef, signal } from '@angular/core';
import { RouterModule } from '@angular/router';
import { FormBuilder, FormGroup, Validators, ReactiveFormsModule } from '@angular/forms';
import { CategoryService } from '../../../core/services/category.service';
import { ToastService } from '../../../core/services/toast.service';
import { Category, CategoryCreateRequest, CategoryUpdateRequest } from '../../../models';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize } from 'rxjs/operators';
import { ConfirmationDialog } from '../../../shared/components/confirmation-dialog/confirmation-dialog';

interface CategoryNode extends Category {
  children: CategoryNode[];
  level: number;
}

@Component({
  selector: 'app-category-tree',
  standalone: true,
  imports: [RouterModule, ReactiveFormsModule, ConfirmationDialog],
  templateUrl: './category-tree.html',
  styleUrl: './category-tree.scss'
})
export class CategoryTreeComponent implements OnInit {
  private fb = inject(FormBuilder);
  private categoryService = inject(CategoryService);
  private toastService = inject(ToastService);
  private destroyRef = inject(DestroyRef);

  categories = signal<Category[]>([]);
  categoryTree = signal<CategoryNode[]>([]);
  flattenedCategories = signal<CategoryNode[]>([]);
  loading = signal(true);

  showModal = signal(false);
  isEditMode = signal(false);
  categoryForm!: FormGroup;
  editingCategoryId = signal<string | null>(null);
  pendingDeleteCategory = signal<CategoryNode | null>(null);

  ngOnInit(): void {
    this.initForm();
    this.loadCategories();
  }

  initForm(): void {
    this.categoryForm = this.fb.group({
      name: ['', [Validators.required, Validators.maxLength(100)]],
      parentId: ['']
    });
  }

  loadCategories(): void {
    this.loading.set(true);
    this.categoryService.getCategories()
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.loading.set(false))
      )
      .subscribe({
        next: (categories) => {
          this.categories.set(categories);
          const tree = this.buildTree(categories);
          this.categoryTree.set(tree);
          this.flattenedCategories.set(this.flattenTree(tree));
        },
        error: (error) => {
          console.error('Failed to load categories:', error);
          this.toastService.error('Failed to load categories');
        }
      });
  }

  buildTree(categories: Category[]): CategoryNode[] {
    const map = new Map<string, CategoryNode>();
    const roots: CategoryNode[] = [];

    categories.forEach(cat => map.set(cat.id, { ...cat, children: [], level: 0 }));

    categories.forEach(cat => {
      const node = map.get(cat.id)!;
      if (cat.parentId) {
        const parent = map.get(cat.parentId);
        if (parent) { node.level = parent.level + 1; parent.children.push(node); }
        else { roots.push(node); }
      } else {
        roots.push(node);
      }
    });

    const sortNodes = (nodes: CategoryNode[]) => {
      nodes.sort((a, b) => a.name.localeCompare(b.name));
      nodes.forEach(node => sortNodes(node.children));
    };
    sortNodes(roots);
    return roots;
  }

  flattenTree(nodes: CategoryNode[]): CategoryNode[] {
    const result: CategoryNode[] = [];
    const flatten = (nodes: CategoryNode[]) => {
      nodes.forEach(node => { result.push(node); if (node.children.length > 0) flatten(node.children); });
    };
    flatten(nodes);
    return result;
  }

  openCreateModal(parentId?: string): void {
    this.isEditMode.set(false);
    this.editingCategoryId.set(null);
    this.categoryForm.reset({ name: '', parentId: parentId || '' });
    this.showModal.set(true);
  }

  openEditModal(category: Category): void {
    this.isEditMode.set(true);
    this.editingCategoryId.set(category.id);
    this.categoryForm.patchValue({ name: category.name, parentId: category.parentId || '' });
    this.showModal.set(true);
  }

  closeModal(): void { this.showModal.set(false); this.categoryForm.reset(); }

  onSubmit(): void {
    if (this.categoryForm.invalid) { this.categoryForm.markAllAsTouched(); return; }

    const formValue = this.categoryForm.value;
    const request: CategoryCreateRequest | CategoryUpdateRequest = {
      name: formValue.name!,
      parentId: formValue.parentId || undefined
    };

    const editingCategoryId = this.editingCategoryId();
    const operation = this.isEditMode() && editingCategoryId
      ? this.categoryService.updateCategory(editingCategoryId, request as CategoryUpdateRequest)
      : this.categoryService.createCategory(request as CategoryCreateRequest);

    operation
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.toastService.success(this.isEditMode() ? 'Category updated successfully' : 'Category created successfully');
          this.closeModal();
          this.loadCategories();
        },
        error: (error) => {
          console.error('Failed to save category:', error);
          this.toastService.error(error.error?.message || 'Failed to save category');
        }
      });
  }

  deleteCategory(category: CategoryNode): void {
    if (this.categories().some(c => c.parentId === category.id)) {
      this.toastService.error('Cannot delete category with subcategories');
      return;
    }
    this.pendingDeleteCategory.set(category);
  }

  requestDelete(category: CategoryNode): void {
    this.deleteCategory(category);
  }

  confirmDelete(): void {
    const category = this.pendingDeleteCategory();
    if (!category) return;
    this.pendingDeleteCategory.set(null);

    this.categoryService.deleteCategory(category.id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => { this.toastService.success('Category deleted successfully'); this.loadCategories(); },
        error: (error) => {
          console.error('Failed to delete category:', error);
          this.toastService.error(error.error?.message || 'Failed to delete category');
        }
      });
  }

  cancelDelete(): void {
    this.pendingDeleteCategory.set(null);
  }

  getAvailableParents(excludeId?: string): Category[] {
    const categories = this.categories();
    if (!excludeId) return categories;
    const descendants = new Set<string>();
    const collectDescendants = (parentId: string) => {
      categories.forEach(cat => {
        if (cat.parentId === parentId && !descendants.has(cat.id)) {
          descendants.add(cat.id);
          collectDescendants(cat.id);
        }
      });
    };
    collectDescendants(excludeId);
    return categories.filter(c => c.id !== excludeId && !descendants.has(c.id));
  }

  getCategoryPath(category: Category | CategoryNode): string {
    const path: string[] = [category.name];
    let currentId = category.parentId;
    const categories = this.categories();
    while (currentId) {
      const parent = categories.find(c => c.id === currentId);
      if (parent) { path.unshift(parent.name); currentId = parent.parentId; }
      else break;
    }
    return path.join(' > ');
  }

  get name() { return this.categoryForm.get('name'); }
  get parentId() { return this.categoryForm.get('parentId'); }
}

