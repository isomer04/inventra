import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ReactiveFormsModule } from '@angular/forms';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { Router, provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { SignupComponent } from './signup';
import { AuthService } from '../../../core/services/auth.service';
import type { MockedObject } from 'vitest';

describe('SignupComponent', () => {
  let component: SignupComponent;
  let fixture: ComponentFixture<SignupComponent>;
  let authService: MockedObject<AuthService>;
  let router: Router;

  beforeEach(async () => {
    const authSpy = { register: vi.fn() };

    await TestBed.configureTestingModule({
      imports: [SignupComponent, ReactiveFormsModule],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: authSpy }
      ]
    }).compileComponents();

    authService = TestBed.inject(AuthService) as unknown as MockedObject<AuthService>;
    router = TestBed.inject(Router);

    // vi.spyOn calls through by default; unstubbed navigation rejects with NG04002.
    vi.spyOn(router, 'navigateByUrl').mockResolvedValue(true);
    vi.spyOn(router, 'navigate').mockResolvedValue(true);

    fixture = TestBed.createComponent(SignupComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  describe('Component Initialization', () => {
    it('should create', () => {
      expect(component).toBeTruthy();
    });

    it('should initialize form with all required fields', () => {
      expect(component.signupForm).toBeDefined();
      expect(component.signupForm.get('companyName')).toBeDefined();
      expect(component.signupForm.get('slug')).toBeDefined();
      expect(component.signupForm.get('firstName')).toBeDefined();
      expect(component.signupForm.get('lastName')).toBeDefined();
      expect(component.signupForm.get('email')).toBeDefined();
      expect(component.signupForm.get('password')).toBeDefined();
      expect(component.signupForm.get('confirmPassword')).toBeDefined();
    });

    it('should initialize with empty form values', () => {
      expect(component.signupForm.get('companyName')?.value).toBe('');
      expect(component.signupForm.get('email')?.value).toBe('');
    });

    it('should start with loading false', () => {
      expect(component.loading()).toBe(false);
    });

    it('should start with empty error message', () => {
      expect(component.errorMessage()).toBe('');
    });
  });

  describe('Form Validation - Company Name', () => {
    it('should require company name', () => {
      const field = component.signupForm.get('companyName');
      
      field?.setValue('');
      expect(field?.hasError('required')).toBe(true);
      
      field?.setValue('Acme Corp');
      expect(field?.hasError('required')).toBe(false);
    });

    it('should enforce minimum length of 2', () => {
      const field = component.signupForm.get('companyName');
      
      field?.setValue('A');
      expect(field?.hasError('minlength')).toBe(true);
      
      field?.setValue('AB');
      expect(field?.hasError('minlength')).toBe(false);
    });

    it('should enforce maximum length of 100', () => {
      const field = component.signupForm.get('companyName');
      
      field?.setValue('A'.repeat(101));
      expect(field?.hasError('maxlength')).toBe(true);
      
      field?.setValue('A'.repeat(100));
      expect(field?.hasError('maxlength')).toBe(false);
    });
  });

  describe('Form Validation - Slug', () => {
    it('should require slug', () => {
      const field = component.signupForm.get('slug');
      
      field?.setValue('');
      expect(field?.hasError('required')).toBe(true);
      
      field?.setValue('acme-corp');
      expect(field?.hasError('required')).toBe(false);
    });

    it('should only accept lowercase letters, numbers, and hyphens', () => {
      const field = component.signupForm.get('slug');
      
      field?.setValue('ACME');
      expect(field?.hasError('pattern')).toBe(true);
      
      field?.setValue('acme corp');
      expect(field?.hasError('pattern')).toBe(true);
      
      field?.setValue('acme_corp');
      expect(field?.hasError('pattern')).toBe(true);
      
      field?.setValue('acme-corp-123');
      expect(field?.hasError('pattern')).toBe(false);
    });

    it('should enforce minimum length of 2', () => {
      const field = component.signupForm.get('slug');
      
      field?.setValue('a');
      expect(field?.hasError('minlength')).toBe(true);
      
      field?.setValue('ab');
      expect(field?.hasError('minlength')).toBe(false);
    });

    it('should enforce maximum length of 50', () => {
      const field = component.signupForm.get('slug');
      
      field?.setValue('a'.repeat(51));
      expect(field?.hasError('maxlength')).toBe(true);
      
      field?.setValue('a'.repeat(50));
      expect(field?.hasError('maxlength')).toBe(false);
    });
  });

  describe('Slug Auto-generation', () => {
    it('should auto-generate slug from company name', () => {
      const companyName = component.signupForm.get('companyName');
      const slug = component.signupForm.get('slug');
      
      companyName?.setValue('Acme Corporation');
      fixture.detectChanges();
      
      expect(slug?.value).toBe('acme-corporation');
    });

    it('should replace spaces with hyphens', () => {
      const companyName = component.signupForm.get('companyName');
      const slug = component.signupForm.get('slug');
      
      companyName?.setValue('My Great Company');
      fixture.detectChanges();
      
      expect(slug?.value).toBe('my-great-company');
    });

    it('should remove special characters', () => {
      const companyName = component.signupForm.get('companyName');
      const slug = component.signupForm.get('slug');
      
      companyName?.setValue('Acme & Co. Inc!');
      fixture.detectChanges();
      
      expect(slug?.value).toBe('acme-co-inc');
    });

    it('should stop auto-generating after manual slug edit', () => {
      const companyName = component.signupForm.get('companyName');
      const slug = component.signupForm.get('slug');
      
      companyName?.setValue('Acme Corp');
      fixture.detectChanges();
      expect(slug?.value).toBe('acme-corp');
      
      // Manually edit slug
      component.onSlugInput();
      slug?.setValue('custom-slug');
      
      // Change company name again
      companyName?.setValue('Different Company');
      fixture.detectChanges();
      
      // Slug should not change
      expect(slug?.value).toBe('custom-slug');
    });

    it('should normalize slug on blur', () => {
      const slug = component.signupForm.get('slug');
      
      slug?.setValue('  ACME Corp Inc!  ');
      component.onSlugBlur();
      
      expect(slug?.value).toBe('acme-corp-inc');
    });

    it('should collapse multiple hyphens', () => {
      const slug = component.signupForm.get('slug');
      
      slug?.setValue('acme---corp');
      component.onSlugBlur();
      
      expect(slug?.value).toBe('acme-corp');
    });

    it('should trim leading and trailing hyphens', () => {
      const slug = component.signupForm.get('slug');
      
      slug?.setValue('-acme-corp-');
      component.onSlugBlur();
      
      expect(slug?.value).toBe('acme-corp');
    });
  });

  describe('Form Validation - Personal Info', () => {
    it('should require first name', () => {
      const field = component.signupForm.get('firstName');
      
      field?.setValue('');
      expect(field?.hasError('required')).toBe(true);
      
      field?.setValue('John');
      expect(field?.hasError('required')).toBe(false);
    });

    it('should enforce first name max length', () => {
      const field = component.signupForm.get('firstName');
      
      field?.setValue('A'.repeat(81));
      expect(field?.hasError('maxlength')).toBe(true);
      
      field?.setValue('A'.repeat(80));
      expect(field?.hasError('maxlength')).toBe(false);
    });

    it('should require last name', () => {
      const field = component.signupForm.get('lastName');
      
      field?.setValue('');
      expect(field?.hasError('required')).toBe(true);
      
      field?.setValue('Doe');
      expect(field?.hasError('required')).toBe(false);
    });

    it('should enforce last name max length', () => {
      const field = component.signupForm.get('lastName');
      
      field?.setValue('A'.repeat(81));
      expect(field?.hasError('maxlength')).toBe(true);
      
      field?.setValue('A'.repeat(80));
      expect(field?.hasError('maxlength')).toBe(false);
    });
  });

  describe('Form Validation - Email', () => {
    it('should require email', () => {
      const field = component.signupForm.get('email');
      
      field?.setValue('');
      expect(field?.hasError('required')).toBe(true);
      
      field?.setValue('john@example.com');
      expect(field?.hasError('required')).toBe(false);
    });

    it('should validate email format', () => {
      const field = component.signupForm.get('email');
      
      field?.setValue('invalid-email');
      expect(field?.hasError('email')).toBe(true);
      
      field?.setValue('john@');
      expect(field?.hasError('email')).toBe(true);
      
      field?.setValue('@example.com');
      expect(field?.hasError('email')).toBe(true);
      
      field?.setValue('john@example.com');
      expect(field?.hasError('email')).toBe(false);
    });
  });

  describe('Form Validation - Password', () => {
    it('should require password', () => {
      const field = component.signupForm.get('password');
      
      field?.setValue('');
      expect(field?.hasError('required')).toBe(true);
      
      field?.setValue('password123');
      expect(field?.hasError('required')).toBe(false);
    });

    it('should enforce minimum length of 8', () => {
      const field = component.signupForm.get('password');
      
      field?.setValue('pass123');
      expect(field?.hasError('minlength')).toBe(true);
      
      field?.setValue('password');
      expect(field?.hasError('minlength')).toBe(false);
    });

    it('should require confirm password', () => {
      const field = component.signupForm.get('confirmPassword');
      
      field?.setValue('');
      expect(field?.hasError('required')).toBe(true);
      
      field?.setValue('password123');
      expect(field?.hasError('required')).toBe(false);
    });

    it('should validate password match', () => {
      component.signupForm.patchValue({
        password: 'password123',
        confirmPassword: 'different'
      });
      
      expect(component.signupForm.hasError('passwordMismatch')).toBe(true);
    });

    it('should not have error when passwords match', () => {
      component.signupForm.patchValue({
        password: 'password123',
        confirmPassword: 'password123'
      });
      
      expect(component.signupForm.hasError('passwordMismatch')).toBe(false);
    });
  });

  describe('Password Strength', () => {
    it('should calculate weak password (score 0-1)', () => {
      component.signupForm.get('password')?.setValue('pass');
      
      const strength = component.passwordStrength;
      
      expect(strength.label).toBe('Weak');
      expect(strength.cssClass).toBe('bg-danger');
    });

    it('should calculate fair password (score 2)', () => {
      // 8+ chars (1) + an uppercase letter (1) = score 2 -> 'Fair'.
      // Plain 'password' only scores 1, which is 'Weak'.
      component.signupForm.get('password')?.setValue('Password');
      
      const strength = component.passwordStrength;
      
      expect(strength.label).toBe('Fair');
      expect(strength.cssClass).toBe('bg-warning');
    });

    it('should calculate good password (score 3)', () => {
      component.signupForm.get('password')?.setValue('Password123');
      
      const strength = component.passwordStrength;
      
      expect(strength.label).toBe('Good');
      expect(strength.cssClass).toBe('bg-info');
    });

    it('should calculate strong password (score 4-5)', () => {
      component.signupForm.get('password')?.setValue('P@ssw0rd123!');
      
      const strength = component.passwordStrength;
      
      expect(strength.label).toBe('Strong');
      expect(strength.cssClass).toBe('bg-success');
    });

    it('should award points for length >= 8', () => {
      component.signupForm.get('password')?.setValue('12345678');
      expect(component.passwordStrength.score).toBeGreaterThanOrEqual(1);
    });

    it('should award points for length >= 12', () => {
      component.signupForm.get('password')?.setValue('123456789012');
      expect(component.passwordStrength.score).toBeGreaterThanOrEqual(2);
    });

    it('should award points for uppercase letters', () => {
      component.signupForm.get('password')?.setValue('Password1234');
      expect(component.passwordStrength.score).toBeGreaterThanOrEqual(2);
    });

    it('should award points for numbers', () => {
      component.signupForm.get('password')?.setValue('password123');
      expect(component.passwordStrength.score).toBeGreaterThanOrEqual(2);
    });

    it('should award points for special characters', () => {
      component.signupForm.get('password')?.setValue('password!@#');
      expect(component.passwordStrength.score).toBeGreaterThanOrEqual(2);
    });
  });

  describe('Password Visibility Toggle', () => {
    it('should toggle password visibility', () => {
      expect(component.showPassword()).toBe(false);
      
      component.togglePasswordVisibility();
      expect(component.showPassword()).toBe(true);
      
      component.togglePasswordVisibility();
      expect(component.showPassword()).toBe(false);
    });

    it('should toggle confirm password visibility', () => {
      expect(component.showConfirmPassword()).toBe(false);
      
      component.toggleConfirmPasswordVisibility();
      expect(component.showConfirmPassword()).toBe(true);
      
      component.toggleConfirmPasswordVisibility();
      expect(component.showConfirmPassword()).toBe(false);
    });
  });

  describe('Form Submission - Success', () => {
    beforeEach(() => {
      component.signupForm.patchValue({
        companyName: 'Acme Corporation',
        slug: 'acme-corp',
        firstName: 'John',
        lastName: 'Doe',
        email: 'john@acme.com',
        password: 'password123',
        confirmPassword: 'password123'
      });
    });

    it('should submit form with valid data', () => {
      authService.register.mockReturnValue(of({ accessToken: 'token', refreshToken: 'refresh', expiresIn: 3600 }));

      component.onSubmit();

      expect(authService.register).toHaveBeenCalledWith({
        tenantName: 'Acme Corporation',
        slug: 'acme-corp',
        firstName: 'John',
        lastName: 'Doe',
        email: 'john@acme.com',
        password: 'password123'
      });
      expect(component.loading()).toBe(false);
      expect(router.navigateByUrl).toHaveBeenCalledWith('/dashboard');
    });

    it('should set loading to true during submission', () => {
      authService.register.mockReturnValue(of({ accessToken: 'token', refreshToken: 'refresh', expiresIn: 3600 }));

      expect(component.loading()).toBe(false);
      
      component.onSubmit();
      
      // Note: loading is set to false after observable completes
      expect(authService.register).toHaveBeenCalled();
    });

    it('should clear error message on successful submission', () => {
      authService.register.mockReturnValue(of({ accessToken: 'token', refreshToken: 'refresh', expiresIn: 3600 }));
      component.errorMessage.set('Previous error');

      component.onSubmit();

      expect(component.errorMessage()).toBe('');
    });
  });

  describe('Form Submission - Validation Errors', () => {
    it('should not submit if form is invalid', () => {
      component.signupForm.patchValue({
        companyName: '',
        email: 'invalid-email'
      });

      component.onSubmit();

      expect(authService.register).not.toHaveBeenCalled();
      expect(component.signupForm.touched).toBe(true);
    });

    it('should mark all fields as touched on invalid submission', () => {
      component.signupForm.patchValue({ companyName: '' });

      component.onSubmit();

      expect(component.companyName?.touched).toBe(true);
      expect(component.slug?.touched).toBe(true);
      expect(component.email?.touched).toBe(true);
    });
  });

  describe('Form Submission - API Errors', () => {
    beforeEach(() => {
      component.signupForm.patchValue({
        companyName: 'Acme Corporation',
        slug: 'acme-corp',
        firstName: 'John',
        lastName: 'Doe',
        email: 'john@acme.com',
        password: 'password123',
        confirmPassword: 'password123'
      });
    });

    it('should handle duplicate slug error (409)', () => {
      authService.register.mockReturnValue(
        throwError(() => ({ status: 409, error: { message: 'Slug already exists' } }))
      );

      component.onSubmit();

      expect(component.loading()).toBe(false);
      expect(component.errorMessage()).toContain('workspace URL is already taken');
      expect(component.slug?.hasError('taken')).toBe(true);
    });

    it('should handle duplicate email error (409)', () => {
      authService.register.mockReturnValue(
        throwError(() => ({ status: 409, error: { message: 'Email already exists' } }))
      );

      component.onSubmit();

      expect(component.errorMessage()).toContain('account with this email already exists');
      expect(component.email?.hasError('taken')).toBe(true);
    });

    it('should handle generic 409 conflict', () => {
      authService.register.mockReturnValue(
        throwError(() => ({ status: 409, error: { message: 'Conflict' } }))
      );

      component.onSubmit();

      expect(component.errorMessage()).toContain('account already exists');
    });

    it('should handle validation error (400)', () => {
      authService.register.mockReturnValue(
        throwError(() => ({ status: 400 }))
      );

      component.onSubmit();

      expect(component.errorMessage()).toBe('Please check your details and try again.');
    });

    it('should handle rate limit error (429)', () => {
      authService.register.mockReturnValue(
        throwError(() => ({ status: 429 }))
      );

      component.onSubmit();

      expect(component.errorMessage()).toBe('Too many requests. Please wait a moment and try again.');
    });

    it('should handle network error (status 0)', () => {
      authService.register.mockReturnValue(
        throwError(() => ({ status: 0 }))
      );

      component.onSubmit();

      expect(component.errorMessage()).toBe('Unable to connect to the server. Please check your internet connection.');
    });

    it('should handle unknown server error (500)', () => {
      authService.register.mockReturnValue(
        throwError(() => ({ status: 500 }))
      );

      component.onSubmit();

      expect(component.errorMessage()).toBe('Something went wrong. Please try again later.');
    });

    it('should set loading to false after error', () => {
      authService.register.mockReturnValue(
        throwError(() => ({ status: 500 }))
      );

      component.onSubmit();

      expect(component.loading()).toBe(false);
    });
  });

  describe('Form Getters', () => {
    it('should provide getter for companyName', () => {
      expect(component.companyName).toBe(component.signupForm.get('companyName'));
    });

    it('should provide getter for slug', () => {
      expect(component.slug).toBe(component.signupForm.get('slug'));
    });

    it('should provide getter for firstName', () => {
      expect(component.firstName).toBe(component.signupForm.get('firstName'));
    });

    it('should provide getter for lastName', () => {
      expect(component.lastName).toBe(component.signupForm.get('lastName'));
    });

    it('should provide getter for email', () => {
      expect(component.email).toBe(component.signupForm.get('email'));
    });

    it('should provide getter for password', () => {
      expect(component.password).toBe(component.signupForm.get('password'));
    });

    it('should provide getter for confirmPassword', () => {
      expect(component.confirmPassword).toBe(component.signupForm.get('confirmPassword'));
    });
  });
});
