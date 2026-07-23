export interface Category {
  id: string;
  tenantId: string;
  name: string;
  parentId?: string;
  parentName?: string;
  createdAt: string;
}

export interface CreateCategoryRequest {
  name: string;
  parentId?: string;
}

export interface UpdateCategoryRequest {
  name?: string;
  parentId?: string;
}

export type CategoryCreateRequest = CreateCategoryRequest;
export type CategoryUpdateRequest = UpdateCategoryRequest;
