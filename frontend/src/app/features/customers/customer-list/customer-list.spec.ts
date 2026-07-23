import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { of, throwError } from 'rxjs';
import type { MockedObject } from 'vitest';
import { CustomerListComponent } from './customer-list';
import { CustomerService } from '../../../core/services/customer.service';
import { ToastService } from '../../../core/services/toast.service';
import { Customer, CustomerStatus, Page } from '../../../models';

describe('CustomerListComponent', () => {
  let component: CustomerListComponent;
  let fixture: ComponentFixture<CustomerListComponent>;
  let customerService: MockedObject<CustomerService>;
  let toastService: MockedObject<ToastService>;

  const mockCustomers: Customer[] = [
    { id: 'cust-1', name: 'Acme Corp', email: 'acme@example.com', phone: '555-0001', status: CustomerStatus.ACTIVE, tenantId: 't1', createdAt: '2026-01-01T00:00:00Z', updatedAt: '2026-01-01T00:00:00Z' },
    { id: 'cust-2', name: 'Beta LLC', email: 'beta@example.com', phone: '555-0002', status: CustomerStatus.ACTIVE, tenantId: 't1', createdAt: '2026-01-02T00:00:00Z', updatedAt: '2026-01-02T00:00:00Z' },
    { id: 'cust-3', name: 'Gamma Inc', email: 'gamma@example.com', phone: '555-0003', status: CustomerStatus.INACTIVE, tenantId: 't1', createdAt: '2026-01-03T00:00:00Z', updatedAt: '2026-01-03T00:00:00Z' }
  ];

  const mockPage: Page<Customer> = {
    content: mockCustomers,
    number: 0,
    size: 10,
    totalElements: 3,
    totalPages: 1
  };

  beforeEach(async () => {
    const customerSpy = { getCustomers: vi.fn(), deleteCustomer: vi.fn() };
    const toastSpy = { success: vi.fn(), error: vi.fn() };

    await TestBed.configureTestingModule({
      imports: [CustomerListComponent, FormsModule],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: CustomerService, useValue: customerSpy },
        { provide: ToastService, useValue: toastSpy }
      ]
    }).compileComponents();

    customerService = TestBed.inject(CustomerService) as unknown as MockedObject<CustomerService>;
    toastService = TestBed.inject(ToastService) as unknown as MockedObject<ToastService>;

    customerService.getCustomers.mockReturnValue(of(mockPage));

    fixture = TestBed.createComponent(CustomerListComponent);
    component = fixture.componentInstance;
  });

  describe('Component Initialization', () => {
    it('should create', () => {
      expect(component).toBeTruthy();
    });

    it('should load customers on init', () => {
      fixture.detectChanges();
      
      expect(customerService.getCustomers).toHaveBeenCalled();
      expect(component.customers().length).toBe(3);
    });

    it('should set loading to false after loading customers', () => {
      fixture.detectChanges();
      
      expect(component.loading()).toBe(false);
    });

    it('should initialize with page 0', () => {
      fixture.detectChanges();
      
      expect(component.currentPage()).toBe(0);
    });
  });

  describe('Customer Loading', () => {
    it('should display customers in the list', () => {
      fixture.detectChanges();
      
      const customers = component.customers();
      expect(customers[0].name).toBe('Acme Corp');
      expect(customers[1].name).toBe('Beta LLC');
      expect(customers[2].name).toBe('Gamma Inc');
    });

    it('should set pagination values', () => {
      fixture.detectChanges();
      
      expect(component.totalElements()).toBe(3);
      expect(component.totalPages()).toBe(1);
    });

    it('should show error toast on load failure', () => {
      customerService.getCustomers.mockReturnValue(throwError(() => ({ status: 500 })));
      
      fixture.detectChanges();
      
      expect(toastService.error).toHaveBeenCalledWith('Failed to load customers');
    });

    it('should send sort parameter', () => {
      fixture.detectChanges();
      
      expect(customerService.getCustomers).toHaveBeenCalledWith(expect.objectContaining({
        sort: 'name,asc'
      }));
    });
  });

  describe('Search Functionality', () => {
    beforeEach(() => {
      fixture.detectChanges();
    });

    it('should filter customers by search term', () => {
      component.searchTerm = 'Acme';
      component.onSearch();
      
      expect(customerService.getCustomers).toHaveBeenCalledWith(expect.objectContaining({
        search: 'Acme'
      }));
    });

    it('should reset to page 0 when searching', () => {
      component.currentPage.set(2);
      component.searchTerm = 'Beta';
      
      component.onSearch();
      
      expect(component.currentPage()).toBe(0);
    });

    it('should not include search parameter when empty', () => {
      component.searchTerm = '';
      component.onSearch();
      
      const calls = customerService.getCustomers.mock.calls;
      const callArgs = calls[calls.length - 1]![0]!;
      expect(callArgs['search']).toBeUndefined();
    });
  });

  describe('Status Filtering', () => {
    beforeEach(() => {
      fixture.detectChanges();
    });

    it('should filter by ACTIVE status', () => {
      component.selectedStatus = CustomerStatus.ACTIVE;
      component.onFilterChange();
      
      expect(customerService.getCustomers).toHaveBeenCalledWith(expect.objectContaining({
        status: CustomerStatus.ACTIVE
      }));
    });

    it('should filter by INACTIVE status', () => {
      component.selectedStatus = CustomerStatus.INACTIVE;
      component.onFilterChange();
      
      expect(customerService.getCustomers).toHaveBeenCalledWith(expect.objectContaining({
        status: CustomerStatus.INACTIVE
      }));
    });

    it('should reset to page 0 when filtering', () => {
      component.currentPage.set(3);
      component.selectedStatus = CustomerStatus.ACTIVE;
      
      component.onFilterChange();
      
      expect(component.currentPage()).toBe(0);
    });

    it('should not include status parameter when empty', () => {
      component.selectedStatus = '';
      component.onFilterChange();
      
      const calls = customerService.getCustomers.mock.calls;
      const callArgs = calls[calls.length - 1]![0]!;
      expect(callArgs['status']).toBeUndefined();
    });
  });

  describe('Combined Filters', () => {
    beforeEach(() => {
      fixture.detectChanges();
    });

    it('should apply both search and status filters', () => {
      component.searchTerm = 'Acme';
      component.selectedStatus = CustomerStatus.ACTIVE;
      
      component.onSearch();
      
      expect(customerService.getCustomers).toHaveBeenCalledWith(expect.objectContaining({
        search: 'Acme',
        status: CustomerStatus.ACTIVE
      }));
    });

    it('should clear all filters', () => {
      component.searchTerm = 'Acme';
      component.selectedStatus = CustomerStatus.ACTIVE;
      component.currentPage.set(2);
      
      component.clearFilters();
      
      expect(component.searchTerm).toBe('');
      expect(component.selectedStatus).toBe('');
      expect(component.currentPage()).toBe(0);
      expect(customerService.getCustomers).toHaveBeenCalled();
    });
  });

  describe('Pagination', () => {
    beforeEach(() => {
      const multiPageMock: Page<Customer> = {
        content: mockCustomers,
        number: 0,
        size: 10,
        totalElements: 50,
        totalPages: 5
      };
      // The component re-syncs currentPage from each response, so the mock has
      // to echo back the page it was asked for, the way the real API does.
      customerService.getCustomers.mockImplementation((params?: { page?: number }) =>
        of({ ...multiPageMock, number: params?.page ?? 0 })
      );
      fixture.detectChanges();
    });

    it('should navigate to specific page', () => {
      component.goToPage(2);
      
      expect(component.currentPage()).toBe(2);
      expect(customerService.getCustomers).toHaveBeenCalledWith(expect.objectContaining({
        page: 2
      }));
    });

    it('should not navigate to negative page', () => {
      const initialPage = component.currentPage();
      
      component.goToPage(-1);
      
      expect(component.currentPage()).toBe(initialPage);
    });

    it('should not navigate beyond total pages', () => {
      const initialPage = component.currentPage();
      
      component.goToPage(10);
      
      expect(component.currentPage()).toBe(initialPage);
    });

    it('should calculate page numbers for display', () => {
      component.currentPage.set(2);
      
      const pageNumbers = component.pageNumbers();
      
      expect(pageNumbers).toContain(2);
      expect(pageNumbers.length).toBeLessThanOrEqual(5);
    });

    it('should show first pages when on page 0', () => {
      component.currentPage.set(0);
      
      const pageNumbers = component.pageNumbers();
      
      expect(pageNumbers[0]).toBe(0);
    });

    it('should show last pages when on last page', () => {
      component.currentPage.set(4);
      
      const pageNumbers = component.pageNumbers();
      
      expect(pageNumbers[pageNumbers.length - 1]).toBe(4);
    });
  });

  describe('Customer Deletion', () => {
    beforeEach(() => {
      fixture.detectChanges();
    });

    it('should set pending delete customer', () => {
      const customer = mockCustomers[0];
      
      component.requestDelete(customer);
      
      expect(component.pendingDeleteCustomer()).toBe(customer);
    });

    it('should delete customer on confirmation', () => {
      customerService.deleteCustomer.mockReturnValue(of(void 0));
      component.pendingDeleteCustomer.set(mockCustomers[0]);
      
      component.confirmDelete();
      
      expect(customerService.deleteCustomer).toHaveBeenCalledWith('cust-1');
      expect(toastService.success).toHaveBeenCalledWith('Customer deleted successfully');
    });

    it('should reload customers after deletion', () => {
      customerService.deleteCustomer.mockReturnValue(of(void 0));
      component.pendingDeleteCustomer.set(mockCustomers[0]);
      const initialCallCount = customerService.getCustomers.mock.calls.length;
      
      component.confirmDelete();
      
      expect(customerService.getCustomers.mock.calls.length).toBe(initialCallCount + 1);
    });

    it('should clear pending delete after confirmation', () => {
      customerService.deleteCustomer.mockReturnValue(of(void 0));
      component.pendingDeleteCustomer.set(mockCustomers[0]);
      
      component.confirmDelete();
      
      expect(component.pendingDeleteCustomer()).toBeNull();
    });

    it('should show error toast on deletion failure', () => {
      customerService.deleteCustomer.mockReturnValue(
        throwError(() => ({ error: { message: 'Deletion failed' } }))
      );
      component.pendingDeleteCustomer.set(mockCustomers[0]);
      
      component.confirmDelete();
      
      expect(toastService.error).toHaveBeenCalledWith('Deletion failed');
    });

    it('should show generic error message when no message provided', () => {
      customerService.deleteCustomer.mockReturnValue(
        throwError(() => ({ error: {} }))
      );
      component.pendingDeleteCustomer.set(mockCustomers[0]);
      
      component.confirmDelete();
      
      expect(toastService.error).toHaveBeenCalledWith('Failed to delete customer');
    });

    it('should cancel deletion', () => {
      component.pendingDeleteCustomer.set(mockCustomers[0]);
      
      component.cancelDelete();
      
      expect(component.pendingDeleteCustomer()).toBeNull();
      expect(customerService.deleteCustomer).not.toHaveBeenCalled();
    });

    it('should not delete if no pending customer', () => {
      component.pendingDeleteCustomer.set(null);
      
      component.confirmDelete();
      
      expect(customerService.deleteCustomer).not.toHaveBeenCalled();
    });
  });

  describe('Empty State', () => {
    it('should display empty list when no customers', () => {
      const emptyPage: Page<Customer> = {
        content: [],
        number: 0,
        size: 10,
        totalElements: 0,
        totalPages: 0
      };
      customerService.getCustomers.mockReturnValue(of(emptyPage));
      
      fixture.detectChanges();
      
      expect(component.customers().length).toBe(0);
      expect(component.totalElements()).toBe(0);
    });
  });

  describe('Loading State', () => {
    it('should show loading state during fetch', () => {
      expect(component.loading()).toBe(true);
      
      fixture.detectChanges();
      
      expect(component.loading()).toBe(false);
    });

    it('should set loading to false even on error', () => {
      customerService.getCustomers.mockReturnValue(throwError(() => ({ status: 500 })));
      
      fixture.detectChanges();
      
      expect(component.loading()).toBe(false);
    });
  });
});
