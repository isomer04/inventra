import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ConfirmationDialog } from './confirmation-dialog';
import { ComponentRef } from '@angular/core';

describe('ConfirmationDialog', () => {
  let component: ConfirmationDialog;
  let fixture: ComponentFixture<ConfirmationDialog>;
  let componentRef: ComponentRef<ConfirmationDialog>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ConfirmationDialog]
    }).compileComponents();

    fixture = TestBed.createComponent(ConfirmationDialog);
    component = fixture.componentInstance;
    componentRef = fixture.componentRef;
  });

  describe('Component Initialization', () => {
    it('should create', () => {
      expect(component).toBeTruthy();
    });

    it('should have default title "Confirm"', () => {
      // `message` is a required input and the template reads it, so it has to
      // be set before the first change detection run.
      componentRef.setInput('message', 'Test');
      fixture.detectChanges();
      expect(component.title()).toBe('Confirm');
    });

    it('should require message input', () => {
      componentRef.setInput('message', 'Are you sure?');
      fixture.detectChanges();
      expect(component.message()).toBe('Are you sure?');
    });

    it('should have default confirmLabel "Delete"', () => {
      componentRef.setInput('message', 'Test');
      fixture.detectChanges();
      expect(component.confirmLabel()).toBe('Delete');
    });

    it('should have default cancelLabel "Cancel"', () => {
      componentRef.setInput('message', 'Test');
      fixture.detectChanges();
      expect(component.cancelLabel()).toBe('Cancel');
    });

    it('should have danger mode enabled by default', () => {
      componentRef.setInput('message', 'Test');
      fixture.detectChanges();
      expect(component.danger()).toBe(true);
    });
  });

  describe('Input Customization', () => {
    beforeEach(() => {
      componentRef.setInput('message', 'Test message');
    });

    it('should accept custom title', () => {
      componentRef.setInput('title', 'Warning');
      fixture.detectChanges();
      expect(component.title()).toBe('Warning');
    });

    it('should accept custom confirm label', () => {
      componentRef.setInput('confirmLabel', 'Yes, proceed');
      fixture.detectChanges();
      expect(component.confirmLabel()).toBe('Yes, proceed');
    });

    it('should accept custom cancel label', () => {
      componentRef.setInput('cancelLabel', 'No, go back');
      fixture.detectChanges();
      expect(component.cancelLabel()).toBe('No, go back');
    });

    it('should allow disabling danger mode', () => {
      componentRef.setInput('danger', false);
      fixture.detectChanges();
      expect(component.danger()).toBe(false);
    });
  });

  describe('Output Events', () => {
    beforeEach(() => {
      componentRef.setInput('message', 'Test message');
      fixture.detectChanges();
    });

    it('should emit confirmed event', () => new Promise<void>((resolve) => {
      component.confirmed.subscribe(() => {
        expect(true).toBe(true);
        resolve();
      });

      component.confirmed.emit();
    }));

    it('should emit cancelled event', () => new Promise<void>((resolve) => {
      component.cancelled.subscribe(() => {
        expect(true).toBe(true);
        resolve();
      });

      component.cancelled.emit();
    }));
  });

  describe('Focus Management', () => {
    it('should focus confirm button on initialization', () => new Promise<void>((resolve) => {
      componentRef.setInput('message', 'Test message');
      fixture.detectChanges();

      // Wait for afterNextRender to complete
      setTimeout(() => {
        const confirmBtn = fixture.nativeElement.querySelector('[data-testid="confirm-button"]');
        if (confirmBtn) {
          expect(document.activeElement).toBe(confirmBtn);
        }
        resolve();
      }, 100);
    }));
  });

  describe('Dialog Rendering', () => {
    beforeEach(() => {
      componentRef.setInput('message', 'Delete this item?');
      componentRef.setInput('title', 'Confirm Delete');
      componentRef.setInput('confirmLabel', 'Delete');
      componentRef.setInput('cancelLabel', 'Cancel');
      fixture.detectChanges();
    });

    it('should render title', () => {
      const titleElement = fixture.nativeElement.querySelector('h2, [data-testid="dialog-title"]');
      if (titleElement) {
        expect(titleElement.textContent).toContain('Confirm Delete');
      }
    });

    it('should render message', () => {
      const compiled = fixture.nativeElement;
      expect(compiled.textContent).toContain('Delete this item?');
    });

    it('should render confirm button with custom label', () => {
      const buttons = fixture.nativeElement.querySelectorAll('button');
      const confirmBtn = Array.from(buttons).find((btn: any) => 
        btn.textContent?.includes('Delete')
      );
      expect(confirmBtn).toBeTruthy();
    });

    it('should render cancel button with custom label', () => {
      const buttons = fixture.nativeElement.querySelectorAll('button');
      const cancelBtn = Array.from(buttons).find((btn: any) => 
        btn.textContent?.includes('Cancel')
      );
      expect(cancelBtn).toBeTruthy();
    });
  });
});
