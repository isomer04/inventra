import { Component, inject, OnInit, DestroyRef, signal } from '@angular/core';
import {
  FormBuilder,
  FormGroup,
  Validators,
  ReactiveFormsModule,
  AbstractControl,
  ValidationErrors
} from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { AuthService } from '../../../core/services/auth.service';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';

/** Cross-field validator: password and confirmPassword must match */
function passwordMatchValidator(group: AbstractControl): ValidationErrors | null {
  const password = group.get('password')?.value;
  const confirm = group.get('confirmPassword')?.value;
  return password && confirm && password !== confirm ? { passwordMismatch: true } : null;
}

@Component({
  selector: 'app-signup',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './signup.html',
  styleUrl: './signup.scss'
})
export class SignupComponent implements OnInit {
  private fb = inject(FormBuilder);
  private authService = inject(AuthService);
  private router = inject(Router);
  private destroyRef = inject(DestroyRef);

  signupForm!: FormGroup;
  loading = signal(false);
  errorMessage = signal('');
  showPassword = signal(false);
  showConfirmPassword = signal(false);

  ngOnInit(): void {
    this.signupForm = this.fb.group(
      {
        companyName: ['', [Validators.required, Validators.minLength(2), Validators.maxLength(100)]],
        slug: ['', [Validators.required, Validators.pattern(/^[a-z0-9-]+$/), Validators.minLength(2), Validators.maxLength(50)]],
        firstName: ['', [Validators.required, Validators.maxLength(80)]],
        lastName: ['', [Validators.required, Validators.maxLength(80)]],
        email: ['', [Validators.required, Validators.email]],
        password: ['', [Validators.required, Validators.minLength(8)]],
        confirmPassword: ['', Validators.required]
      },
      { validators: passwordMatchValidator }
    );

    this.signupForm.get('companyName')?.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((name: string) => {
        if (!this.slugManuallyEdited) {
          const generated = this.generateSlug(name);
          this.signupForm.get('slug')?.setValue(generated, { emitEvent: false });
        }
      });
  }

  private slugManuallyEdited = false;

  onSlugInput(): void {
    this.slugManuallyEdited = true;
  }

  onSlugBlur(): void {
    const raw = this.signupForm.get('slug')?.value ?? '';
    const normalized = this.generateSlug(raw);
    this.signupForm.get('slug')?.setValue(normalized, { emitEvent: false });
  }

  togglePasswordVisibility(): void {
    this.showPassword.update(v => !v);
  }

  toggleConfirmPasswordVisibility(): void {
    this.showConfirmPassword.update(v => !v);
  }

  get passwordStrength(): { score: number; label: string; cssClass: string } {
    const val: string = this.signupForm.get('password')?.value ?? '';
    let score = 0;
    if (val.length >= 8) score++;
    if (val.length >= 12) score++;
    if (/[A-Z]/.test(val)) score++;
    if (/[0-9]/.test(val)) score++;
    if (/[^A-Za-z0-9]/.test(val)) score++;

    if (score <= 1) return { score, label: 'Weak', cssClass: 'bg-danger' };
    if (score <= 2) return { score, label: 'Fair', cssClass: 'bg-warning' };
    if (score <= 3) return { score, label: 'Good', cssClass: 'bg-info' };
    return { score, label: 'Strong', cssClass: 'bg-success' };
  }

  onSubmit(): void {
    if (this.signupForm.invalid) {
      this.signupForm.markAllAsTouched();
      return;
    }

    this.loading.set(true);
    this.errorMessage.set('');

    const { companyName, slug, firstName, lastName, email, password } = this.signupForm.value;

    this.authService
      .register({ tenantName: companyName, slug, firstName, lastName, email, password })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.loading.set(false);
          this.router.navigateByUrl('/dashboard');
        },
        error: (error: HttpErrorResponse) => {
          this.loading.set(false);
          if (error.status === 409) {
            const msg: string = error.error?.message ?? '';
            if (msg.toLowerCase().includes('slug')) {
              this.errorMessage.set('That workspace URL is already taken. Please choose a different one.');
              this.signupForm.get('slug')?.setErrors({ taken: true });
            } else if (msg.toLowerCase().includes('email')) {
              this.errorMessage.set('An account with this email already exists. Try logging in instead.');
              this.signupForm.get('email')?.setErrors({ taken: true });
            } else {
              this.errorMessage.set('This account already exists. Please try a different email or workspace URL.');
            }
          } else if (error.status === 400) {
            this.errorMessage.set('Please check your details and try again.');
          } else if (error.status === 429) {
            this.errorMessage.set('Too many requests. Please wait a moment and try again.');
          } else if (error.status === 0) {
            this.errorMessage.set('Unable to connect to the server. Please check your internet connection.');
          } else {
            this.errorMessage.set('Something went wrong. Please try again later.');
          }
        }
      });
  }

  private generateSlug(value: string): string {
    return value
      .toLowerCase()
      .trim()
      .replace(/\s+/g, '-')
      .replace(/[^a-z0-9-]/g, '')
      .replace(/-+/g, '-')
      .replace(/^-|-$/g, '');
  }

  get companyName() { return this.signupForm.get('companyName'); }
  get slug() { return this.signupForm.get('slug'); }
  get firstName() { return this.signupForm.get('firstName'); }
  get lastName() { return this.signupForm.get('lastName'); }
  get email() { return this.signupForm.get('email'); }
  get password() { return this.signupForm.get('password'); }
  get confirmPassword() { return this.signupForm.get('confirmPassword'); }
}
