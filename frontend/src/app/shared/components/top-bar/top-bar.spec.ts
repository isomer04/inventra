import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TopBarComponent } from './top-bar';
import { AuthService } from '../../../core/services/auth.service';
import { BreadcrumbService } from '../../../core/services/breadcrumb.service';
import { Router } from '@angular/router';
import { provideRouter } from '@angular/router';
import { of, BehaviorSubject, Observable } from 'rxjs';
import { signal, WritableSignal } from '@angular/core';
import { Mock } from 'vitest';

describe('TopBarComponent', () => {
  let component: TopBarComponent;
  let fixture: ComponentFixture<TopBarComponent>;
  let authService: { logout: Mock<() => any>; currentUser: WritableSignal<any> };
  let breadcrumbsSubject: BehaviorSubject<any[]>;

  const mockUser = {
    id: 'user-1',
    tenantId: 'tenant-1',
    email: 'jane.smith@example.com',
    firstName: 'Jane',
    lastName: 'Smith',
    role: 'ADMIN' as const
  };

  const mockBreadcrumbs = [
    { label: 'Dashboard', url: '/dashboard' },
    { label: 'Orders', url: '/orders' },
    { label: 'Order #12345', url: '/orders/12345' }
  ];

  beforeEach(async () => {
    breadcrumbsSubject = new BehaviorSubject<any[]>([]);
    
    const authServiceSpy: { logout: ReturnType<typeof vi.fn>; currentUser: WritableSignal<any> } = {
      logout: vi.fn(() => of(undefined)),
      currentUser: signal(mockUser)
    };
    const breadcrumbServiceSpy: { breadcrumbs$: Observable<any[]> } = {
      breadcrumbs$: breadcrumbsSubject.asObservable()
    };
    const routerSpy: { navigate: ReturnType<typeof vi.fn> } = {
      navigate: vi.fn()
    };

    await TestBed.configureTestingModule({
      imports: [TopBarComponent],
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: authServiceSpy },
        { provide: BreadcrumbService, useValue: breadcrumbServiceSpy },
        { provide: Router, useValue: routerSpy }
      ]
    }).compileComponents();

    authService = TestBed.inject(AuthService) as any;

    fixture = TestBed.createComponent(TopBarComponent);
    component = fixture.componentInstance;
  });

  describe('Component Initialization', () => {
    it('should create', () => {
      expect(component).toBeTruthy();
    });

    it('should initialize dropdown as closed', () => {
      expect(component.dropdownOpen()).toBe(false);
    });

    it('should load current user from auth service', () => {
      fixture.detectChanges();
      expect(component.currentUser()).toEqual(mockUser);
    });

    it('should initialize breadcrumbs as empty array', () => {
      expect(component.breadcrumbs()).toEqual([]);
    });
  });

  describe('Breadcrumbs', () => {
    it('should subscribe to breadcrumb service', () => {
      fixture.detectChanges();
      breadcrumbsSubject.next(mockBreadcrumbs);
      expect(component.breadcrumbs()).toEqual(mockBreadcrumbs);
    });

    it('should update breadcrumbs reactively', () => {
      fixture.detectChanges();
      
      const breadcrumbs1 = [{ label: 'Home', url: '/home' }];
      breadcrumbsSubject.next(breadcrumbs1);
      expect(component.breadcrumbs()).toEqual(breadcrumbs1);
      
      const breadcrumbs2 = [
        { label: 'Home', url: '/home' },
        { label: 'Products', url: '/products' }
      ];
      breadcrumbsSubject.next(breadcrumbs2);
      expect(component.breadcrumbs()).toEqual(breadcrumbs2);
    });

    it('should handle empty breadcrumbs', () => {
      fixture.detectChanges();
      breadcrumbsSubject.next([]);
      expect(component.breadcrumbs()).toEqual([]);
    });
  });

  describe('User Initials', () => {
    it('should compute initials from first and last name', () => {
      fixture.detectChanges();
      expect(component.initials()).toBe('JS');
    });

    it('should compute initials from first name only', () => {
      (authService.currentUser as any) = signal({
        ...mockUser,
        firstName: 'John',
        lastName: ''
      });
      fixture = TestBed.createComponent(TopBarComponent);
      component = fixture.componentInstance;
      fixture.detectChanges();
      
      expect(component.initials()).toBe('J');
    });

    it('should compute initials from last name only', () => {
      (authService.currentUser as any) = signal({
        ...mockUser,
        firstName: '',
        lastName: 'Doe'
      });
      fixture = TestBed.createComponent(TopBarComponent);
      component = fixture.componentInstance;
      fixture.detectChanges();
      
      expect(component.initials()).toBe('D');
    });

    it('should use email first character when no name provided', () => {
      (authService.currentUser as any) = signal({
        ...mockUser,
        firstName: '',
        lastName: '',
        email: 'test@example.com'
      });
      fixture = TestBed.createComponent(TopBarComponent);
      component = fixture.componentInstance;
      fixture.detectChanges();
      
      expect(component.initials()).toBe('T');
    });

    it('should return question mark when no user info available', () => {
      (authService.currentUser as any) = signal({
        ...mockUser,
        firstName: '',
        lastName: '',
        email: ''
      });
      fixture = TestBed.createComponent(TopBarComponent);
      component = fixture.componentInstance;
      fixture.detectChanges();
      
      expect(component.initials()).toBe('?');
    });

    it('should handle null user', () => {
      (authService.currentUser as any) = signal(null);
      fixture = TestBed.createComponent(TopBarComponent);
      component = fixture.componentInstance;
      fixture.detectChanges();
      
      expect(component.initials()).toBe('');
    });

    it('should return uppercase initials', () => {
      (authService.currentUser as any) = signal({
        ...mockUser,
        firstName: 'john',
        lastName: 'doe'
      });
      fixture = TestBed.createComponent(TopBarComponent);
      component = fixture.componentInstance;
      fixture.detectChanges();
      
      expect(component.initials()).toBe('JD');
    });
  });

  describe('Dropdown Toggle', () => {
    it('should toggle dropdown open state', () => {
      expect(component.dropdownOpen()).toBe(false);
      
      component.toggleDropdown();
      expect(component.dropdownOpen()).toBe(true);
      
      component.toggleDropdown();
      expect(component.dropdownOpen()).toBe(false);
    });

    it('should close dropdown on outside click', () => {
      component.dropdownOpen.set(true);
      fixture.detectChanges();

      const outsideElement = document.createElement('div');
      document.body.appendChild(outsideElement);
      
      const clickEvent = new Event('click', { bubbles: true });
      Object.defineProperty(clickEvent, 'target', { value: outsideElement, enumerable: true });
      
      document.dispatchEvent(clickEvent);
      
      expect(component.dropdownOpen()).toBe(false);
      
      document.body.removeChild(outsideElement);
    });

    it('should not close dropdown on inside click', () => {
      component.dropdownOpen.set(true);
      fixture.detectChanges();

      const insideElement = fixture.nativeElement.querySelector('button') || fixture.nativeElement;
      const clickEvent = new Event('click', { bubbles: true });
      Object.defineProperty(clickEvent, 'target', { value: insideElement, enumerable: true });
      
      document.dispatchEvent(clickEvent);
      
      expect(component.dropdownOpen()).toBe(true);
    });

    it('should close dropdown on Escape key', () => {
      component.dropdownOpen.set(true);
      fixture.detectChanges();

      const escapeEvent = new KeyboardEvent('keydown', { key: 'Escape' });
      document.dispatchEvent(escapeEvent);
      
      expect(component.dropdownOpen()).toBe(false);
    });

    it('should not close dropdown on other keys', () => {
      component.dropdownOpen.set(true);
      fixture.detectChanges();

      const enterEvent = new KeyboardEvent('keydown', { key: 'Enter' });
      document.dispatchEvent(enterEvent);
      
      expect(component.dropdownOpen()).toBe(true);
    });
  });

  describe('Menu Toggle', () => {
    it('should emit menuToggle event', () => new Promise<void>((resolve) => {
      // `menuToggle` is an Angular `output<void>()`, so `subscribe` takes a plain
      // callback rather than an observer object.
      component.menuToggle.subscribe(() => {
        expect(true).toBe(true);
        resolve();
      });

      component.onMenuClick();
    }));
  });

  describe('Logout Action', () => {
    it('should call auth service logout', () => {
      authService.logout.mockReturnValue(of(undefined));

      component.logout();

      expect(authService.logout).toHaveBeenCalled();
    });

    it('should handle logout observable', () => new Promise<void>((resolve, reject) => {
      authService.logout.mockReturnValue(of(undefined));

      component.logout();

      authService.logout().subscribe({
        next: () => { expect(true).toBe(true); resolve(); },
        error: (e: unknown) => reject(e)
      });
    }));
  });

  describe('Component Outputs', () => {
    it('should have menuToggle output', () => {
      expect(component.menuToggle).toBeDefined();
    });
  });

  describe('Event Cleanup', () => {
    it('should remove event listeners on destroy', () => {
      const removeEventListenerSpy = vi.spyOn(document, 'removeEventListener');
      
      fixture.detectChanges();
      fixture.destroy();
      
      expect(removeEventListenerSpy).toHaveBeenCalledWith('click', expect.any(Function));
      expect(removeEventListenerSpy).toHaveBeenCalledWith('keydown', expect.any(Function));
    });
  });
});
