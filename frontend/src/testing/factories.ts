import { Product, ProductStatus } from '../app/models/product.model';
import { Category } from '../app/models/category.model';
import { Customer, CustomerStatus } from '../app/models/customer.model';
import { Order, OrderStatus, OrderItem } from '../app/models/order.model';

function generateId(): string {
  return 'test-' + Math.random().toString(36).substring(2, 15);
}

export function createMockProduct(overrides?: Partial<Product>): Product {
  const id = generateId();
  return {
    id,
    tenantId: 'test-tenant-1',
    sku: `TEST-SKU-${id.substring(5, 13).toUpperCase()}`,
    name: 'Test Product',
    description: 'Test product description',
    categoryId: 'test-category-1',
    categoryName: 'Test Category',
    unitPrice: 99.99,
    unitOfMeasure: 'EA',
    status: ProductStatus.ACTIVE,
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString(),
    ...overrides
  };
}

export function createMockCategory(overrides?: Partial<Category>): Category {
  const id = generateId();
  return {
    id,
    tenantId: 'test-tenant-1',
    name: 'Test Category',
    parentId: undefined,
    parentName: undefined,
    createdAt: new Date().toISOString(),
    ...overrides
  };
}

export function createMockCustomer(overrides?: Partial<Customer>): Customer {
  const id = generateId();
  return {
    id,
    tenantId: 'test-tenant-1',
    name: 'Test Customer',
    email: `test-${id.substring(5, 13)}@example.test`,
    phone: '+1-555-0100',
    address: '123 Test Street, Test City, TC 12345',
    notes: 'Test customer notes',
    status: CustomerStatus.ACTIVE,
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString(),
    ...overrides
  };
}

export function createMockOrderItem(overrides?: Partial<OrderItem>): OrderItem {
  const id = generateId();
  const quantity = overrides?.quantity ?? 10;
  const unitPrice = overrides?.unitPrice ?? 99.99;
  return {
    id,
    orderId: 'test-order-1',
    productId: 'test-product-1',
    productName: 'Test Product',
    productSku: 'TEST-SKU-001',
    quantity,
    unitPrice,
    totalPrice: quantity * unitPrice,
    ...overrides
  };
}

export function createMockOrder(overrides?: Partial<Order>): Order {
  const id = generateId();
  const items = overrides?.items ?? [createMockOrderItem()];
  const totalAmount = items.reduce((sum, item) => sum + item.totalPrice, 0);

  return {
    id,
    tenantId: 'test-tenant-1',
    orderNumber: `ORD-${id.substring(5, 13).toUpperCase()}`,
    customerId: 'test-customer-1',
    customerName: 'Test Customer',
    status: OrderStatus.DRAFT,
    totalAmount,
    notes: 'Test order notes',
    createdBy: 'test-user-1',
    createdByName: 'Test User',
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString(),
    items,
    ...overrides
  };
}
