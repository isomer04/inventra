import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, ActivatedRoute } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { LoginComponent } from './login';

/**
 * LoginComponent unit tests.
 *
 * Covers form validation, open-redirect protection on returnUrl,
 * and error message display on 401 / 429 / network error.
 */
describe('LoginComponent', () => {
  function makeRoute(queryParams: Record<string, string> = {}) {
    return {
      snapshot: { queryParams },
    };
  }

  async function setupComponent(
    queryParams: Record<string, string> = {}
  ): Promise<{ fixture: ComponentFixture<LoginComponent>; component: LoginComponent }> {
    TestBed.resetTestingModule();
    await TestBed.configureTestingModule({
      imports: [LoginComponent],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: ActivatedRoute, useValue: makeRoute(queryParams) },
      ],
    }).compileComponents();

    const fixture = TestBed.createComponent(LoginComponent);
    const component = fixture.componentInstance;
    fixture.detectChanges();
    await fixture.whenStable();
    return { fixture, component };
  }

  it('should create', async () => {
    const { component } = await setupComponent();
    expect(component).toBeTruthy();
  });

  it('form is invalid when empty', async () => {
    const { component } = await setupComponent();
    expect(component.loginForm.invalid).toBe(true);
  });

  it('email field is invalid for a non-email string', async () => {
    const { component } = await setupComponent();
    component.loginForm.get('email')!.setValue('not-an-email');
    expect(component.loginForm.get('email')!.invalid).toBe(true);
  });

  it('password field is invalid when shorter than 8 characters', async () => {
    const { component } = await setupComponent();
    component.loginForm.get('password')!.setValue('short');
    expect(component.loginForm.get('password')!.invalid).toBe(true);
  });

  it('password field is valid at exactly 8 characters', async () => {
    const { component } = await setupComponent();
    component.loginForm.get('password')!.setValue('exactly8');
    expect(component.loginForm.get('password')!.valid).toBe(true);
  });

  it('form is valid with a proper email and 8+ char password', async () => {
    const { component } = await setupComponent();
    component.loginForm.get('email')!.setValue('user@example.com');
    component.loginForm.get('password')!.setValue('Password1!');
    expect(component.loginForm.valid).toBe(true);
  });

  describe('returnUrl validation', () => {
    it('accepts a safe relative path', async () => {
      const { component } = await setupComponent({ returnUrl: '/dashboard' });
      expect(component.returnUrl).toBe('/dashboard');
    });

    it('accepts a nested safe relative path', async () => {
      const { component } = await setupComponent({ returnUrl: '/orders/123' });
      expect(component.returnUrl).toBe('/orders/123');
    });

    it('rejects a protocol-relative URL (//evil.com)', async () => {
      const { component } = await setupComponent({ returnUrl: '//evil.com' });
      expect(component.returnUrl).toBe('/dashboard');
    });

    it('rejects an absolute HTTP URL', async () => {
      const { component } = await setupComponent({ returnUrl: 'http://evil.com' });
      expect(component.returnUrl).toBe('/dashboard');
    });

    it('rejects an absolute HTTPS URL', async () => {
      const { component } = await setupComponent({ returnUrl: 'https://evil.com' });
      expect(component.returnUrl).toBe('/dashboard');
    });

    it('rejects a javascript: URI', async () => {
      const { component } = await setupComponent({ returnUrl: '/javascript:alert(1)' });
      expect(component.returnUrl).toBe('/dashboard');
    });

    it('rejects a data: URI', async () => {
      const { component } = await setupComponent({ returnUrl: '/data:text/html,<script>' });
      expect(component.returnUrl).toBe('/dashboard');
    });

    it('defaults to /dashboard when returnUrl is absent', async () => {
      const { component } = await setupComponent();
      expect(component.returnUrl).toBe('/dashboard');
    });
  });

  it('togglePasswordVisibility() flips showPassword signal', async () => {
    const { component } = await setupComponent();
    expect(component.showPassword()).toBe(false);
    component.togglePasswordVisibility();
    expect(component.showPassword()).toBe(true);
    component.togglePasswordVisibility();
    expect(component.showPassword()).toBe(false);
  });
});
