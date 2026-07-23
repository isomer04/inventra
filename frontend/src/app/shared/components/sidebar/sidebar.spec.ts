import { ComponentFixture, TestBed } from '@angular/core/testing';
import { SidebarComponent } from './sidebar';
import { AuthService } from '../../../core/services/auth.service';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { signal } from '@angular/core';
import { ComponentRef } from '@angular/core';
import { MockedObject } from 'vitest';
import { User, UserRole, UserStatus } from '../../../models';

describe('SidebarComponent', () => {
  let component: SidebarComponent;
  let fixture: ComponentFixture<SidebarComponent>;
  let componentRef: ComponentRef<SidebarComponent>;
  let authService: MockedObject<AuthService>;

  const mockUser: User = {
    id: 'user-1',
    tenantId: 'tenant-1',
    email: 'john.doe@example.com',
    firstName: 'John',
    lastName: 'Doe',
    role: UserRole.ADMIN,
    status: UserStatus.ACTIVE,
    createdAt: '2026-01-01',
    updatedAt: '2026-01-01'
  };

  beforeEach(async () => {
    const authServiceSpy = { logout: vi.fn(), currentUser: signal(mockUser) };

    // The component only pulls in RouterModule for `routerLink`; it never
    // injects Router. Overriding Router here would break the `rootRoute`
    // factory that provideRouter uses to build ActivatedRoute.
    await TestBed.configureTestingModule({
      imports: [SidebarComponent],
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: authServiceSpy }
      ]
    }).compileComponents();

    authService = TestBed.inject(AuthService) as unknown as MockedObject<AuthService>;

    fixture = TestBed.createComponent(SidebarComponent);
    component = fixture.componentInstance;
    componentRef = fixture.componentRef;
  });

  describe('Component Initialization', () => {
    it('should create', () => {
      expect(component).toBeTruthy();
    });

    it('should initialize with collapsed false by default', () => {
      expect(component.collapsed()).toBe(false);
    });

    it('should initialize user dropdown as closed', () => {
      expect(component.userDropdownOpen()).toBe(false);
    });

    it('should load current user from auth service', () => {
      fixture.detectChanges();
      expect(component.currentUser()).toEqual(mockUser);
    });
  });

  describe('Navigation Sections', () => {
    it('should have navigation sections defined', () => {
      expect(component.navSections).toBeDefined();
      expect(component.navSections.length).toBeGreaterThan(0);
    });

    it('should have Overview section with Dashboard', () => {
      const overview = component.navSections.find(s => s.label === 'Overview');
      expect(overview).toBeDefined();
      expect(overview?.items).toContainEqual({
        label: 'Dashboard',
        icon: 'bi-speedometer2',
        route: '/dashboard'
      });
    });

    it('should have Catalog section with Products and Inventory', () => {
      const catalog = component.navSections.find(s => s.label === 'Catalog');
      expect(catalog).toBeDefined();
      expect(catalog?.items.length).toBe(2);
      expect(catalog?.items.find(i => i.label === 'Products')).toBeDefined();
      expect(catalog?.items.find(i => i.label === 'Inventory')).toBeDefined();
    });

    it('should have Commerce section with Orders and Customers', () => {
      const commerce = component.navSections.find(s => s.label === 'Commerce');
      expect(commerce).toBeDefined();
      expect(commerce?.items.length).toBe(2);
      expect(commerce?.items.find(i => i.label === 'Orders')).toBeDefined();
      expect(commerce?.items.find(i => i.label === 'Customers')).toBeDefined();
    });

    it('should have Analytics section with Reports', () => {
      const analytics = component.navSections.find(s => s.label === 'Analytics');
      expect(analytics).toBeDefined();
      expect(analytics?.items).toContainEqual({
        label: 'Reports',
        icon: 'bi-graph-up',
        route: '/reports'
      });
    });

    it('should have correct routes for all navigation items', () => {
      const allItems = component.navSections.flatMap(s => s.items);
      allItems.forEach(item => {
        expect(item.route).toMatch(/^\/[a-z]+$/);
      });
    });

    it('should have icons for all navigation items', () => {
      const allItems = component.navSections.flatMap(s => s.items);
      allItems.forEach(item => {
        expect(item.icon).toBeTruthy();
        expect(item.icon).toContain('bi-');
      });
    });
  });

  describe('Collapsed State', () => {
    it('should accept collapsed input', () => {
      componentRef.setInput('collapsed', true);
      fixture.detectChanges();
      expect(component.collapsed()).toBe(true);
    });

    it('should emit toggleCollapse event', () => new Promise<void>((resolve) => {
      component.toggleCollapse.subscribe(() => {
        expect(true).toBe(true);
        resolve();
      });

      component.onToggle();
    }));
  });

  describe('User Dropdown', () => {
    it('should toggle user dropdown open state', () => {
      expect(component.userDropdownOpen()).toBe(false);
      
      component.toggleUserDropdown();
      expect(component.userDropdownOpen()).toBe(true);
      
      component.toggleUserDropdown();
      expect(component.userDropdownOpen()).toBe(false);
    });

    it('should close dropdown on outside click', () => {
      component.userDropdownOpen.set(true);
      fixture.detectChanges();

      const outsideElement = document.createElement('div');
      document.body.appendChild(outsideElement);
      
      const clickEvent = new Event('click', { bubbles: true });
      Object.defineProperty(clickEvent, 'target', { value: outsideElement, enumerable: true });
      
      document.dispatchEvent(clickEvent);
      
      expect(component.userDropdownOpen()).toBe(false);
      
      document.body.removeChild(outsideElement);
    });

    it('should not close dropdown on inside click', () => {
      component.userDropdownOpen.set(true);
      fixture.detectChanges();

      const insideElement = fixture.nativeElement.querySelector('button') || fixture.nativeElement;
      const clickEvent = new Event('click', { bubbles: true });
      Object.defineProperty(clickEvent, 'target', { value: insideElement, enumerable: true });
      
      document.dispatchEvent(clickEvent);

      expect(component.userDropdownOpen()).toBe(true);
    });
  });

  describe('Logout Action', () => {
    it('should call auth service logout', () => {
      authService.logout.mockReturnValue(of(undefined));

      component.logout();

      expect(authService.logout).toHaveBeenCalled();
    });

    it('should handle logout observable', () => new Promise<void>((resolve) => {
      authService.logout.mockReturnValue(of(undefined));

      component.logout();

      authService.logout().subscribe(() => {
        expect(true).toBe(true);
        resolve();
      });
    }));
  });

  describe('Overlay Close', () => {
    it('should emit overlayClose event', () => new Promise<void>((resolve) => {
      component.overlayClose.subscribe(() => {
        expect(true).toBe(true);
        resolve();
      });

      component.onOverlayClick();
    }));
  });

  describe('Component Outputs', () => {
    it('should have overlayClose output', () => {
      expect(component.overlayClose).toBeDefined();
    });

    it('should have toggleCollapse output', () => {
      expect(component.toggleCollapse).toBeDefined();
    });
  });

  describe('User Display', () => {
    it('should display current user information', () => {
      fixture.detectChanges();
      const user = component.currentUser();
      expect(user?.firstName).toBe('John');
      expect(user?.lastName).toBe('Doe');
      expect(user?.email).toBe('john.doe@example.com');
    });

    it('should handle null user', () => {
      (authService.currentUser as any) = signal(null);
      fixture = TestBed.createComponent(SidebarComponent);
      component = fixture.componentInstance;
      fixture.detectChanges();
      
      expect(component.currentUser()).toBeNull();
    });
  });
});
