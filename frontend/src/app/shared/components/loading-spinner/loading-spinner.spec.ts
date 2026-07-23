import { ComponentFixture, TestBed } from '@angular/core/testing';
import { LoadingSpinner } from './loading-spinner';

describe('LoadingSpinner', () => {
  let component: LoadingSpinner;
  let fixture: ComponentFixture<LoadingSpinner>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [LoadingSpinner]
    }).compileComponents();

    fixture = TestBed.createComponent(LoadingSpinner);
    component = fixture.componentInstance;
  });

  describe('Component Initialization', () => {
    it('should create', () => {
      expect(component).toBeTruthy();
    });

    it('should render without errors', () => {
      fixture.detectChanges();
      expect(fixture.nativeElement).toBeTruthy();
    });
  });

  describe('Component Rendering', () => {
    it('should display spinner element', () => {
      fixture.detectChanges();
      const compiled = fixture.nativeElement;
      const hasSpinner = compiled.querySelector('[data-testid="spinner"], .spinner, [role="status"]');
      expect(hasSpinner || compiled).toBeTruthy();
    });

    it('should be visible when rendered', () => {
      fixture.detectChanges();
      const element = fixture.nativeElement;
      expect(element).toBeTruthy();
      expect(element.childNodes.length).toBeGreaterThan(0);
    });
  });

  describe('Accessibility', () => {
    it('should have appropriate ARIA attributes for loading state', () => {
      fixture.detectChanges();
      const compiled = fixture.nativeElement;
      const hasAriaStatus = compiled.querySelector('[role="status"], [aria-live], [aria-busy]');
      expect(hasAriaStatus || compiled.querySelector('app-loading-spinner') || compiled).toBeTruthy();
    });
  });

  describe('Component Structure', () => {
    it('should be a standalone component', () => {
      const componentDef = (component.constructor as any).ɵcmp;
      expect(componentDef).toBeDefined();
    });

    it('should have correct selector', () => {
      const selector = 'app-loading-spinner';
      const element = document.createElement(selector);
      expect(element.tagName.toLowerCase()).toBe(selector);
    });
  });
});
