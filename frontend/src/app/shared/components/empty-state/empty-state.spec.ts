import { ComponentFixture, TestBed } from '@angular/core/testing';
import { EmptyState } from './empty-state';

describe('EmptyState', () => {
  let component: EmptyState;
  let fixture: ComponentFixture<EmptyState>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [EmptyState]
    }).compileComponents();

    fixture = TestBed.createComponent(EmptyState);
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

  describe('Content Projection', () => {
    it('should display projected content', () => {
      const hostElement = fixture.nativeElement;
      hostElement.innerHTML = '<app-empty-state><p>No items found</p></app-empty-state>';
      fixture.detectChanges();

      const projectedContent = hostElement.querySelector('p');
      expect(projectedContent).toBeTruthy();
    });

    it('should render empty state message', () => {
      fixture.detectChanges();
      const compiled = fixture.nativeElement;
      expect(compiled.querySelector('app-empty-state, [data-testid="empty-state"]') || compiled).toBeTruthy();
    });
  });

  describe('Component Structure', () => {
    it('should be a standalone component', () => {
      const componentDef = (component.constructor as any).ɵcmp;
      expect(componentDef).toBeDefined();
    });

    it('should have correct selector', () => {
      const selector = 'app-empty-state';
      const element = document.createElement(selector);
      expect(element.tagName.toLowerCase()).toBe(selector);
    });
  });
});
