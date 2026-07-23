import { Component, inject, OnInit, DestroyRef, signal } from '@angular/core';
import { ReactiveFormsModule } from '@angular/forms';
import { Router, ActivatedRoute, RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { AuthService } from '../../../core/services/auth.service';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './login.html',
  styleUrl: './login.scss'
})
export class LoginComponent implements OnInit {
  private fb = inject(FormBuilder);
  private authService = inject(AuthService);
  private router = inject(Router);
  private route = inject(ActivatedRoute);
  private destroyRef = inject(DestroyRef);

  loginForm!: FormGroup;
  loading = signal(false);
  errorMessage = signal('');
  returnUrl = '/dashboard';
  showPassword = signal(false);

  ngOnInit(): void {
    this.loginForm = this.fb.group({
      email: ['', [Validators.required, Validators.email]],
      // Align client-side minimum with backend @Size(min=8).
      // Previously minLength(6) — weaker than the server rule, giving users
      // a misleading "valid" state for 6-7 character passwords that the API rejects.
      password: ['', [Validators.required, Validators.minLength(8)]]
    });

    // Validate returnUrl is a safe relative path before using it.
    // A malicious link like /login?returnUrl=//evil.com would pass the old
    // startsWith('/') check. We now validate here at read time so the value
    // stored in returnUrl is always safe.
    const raw = this.route.snapshot.queryParams['returnUrl'] ?? '/dashboard';
    this.returnUrl = this.isSafeReturnUrl(raw) ? raw : '/dashboard';
  }

  /**
   * Returns true only for relative paths that start with a single '/' and
   * do not start with '//' (protocol-relative URL — open redirect vector).
   * Rejects absolute URLs, javascript: URIs, and data: URIs.
   */
  private isSafeReturnUrl(url: string): boolean {
    return typeof url === 'string' &&
           url.startsWith('/') &&
           !url.startsWith('//') &&
           !url.toLowerCase().startsWith('/javascript') &&
           !url.toLowerCase().startsWith('/data');
  }

  togglePasswordVisibility(): void {
    this.showPassword.update(v => !v);
  }

  fillDemoCredentials(): void {
    this.loginForm.patchValue({ email: 'admin@demo.com', password: 'demo1234' });
  }

  onSubmit(): void {
    if (this.loginForm.invalid) { this.loginForm.markAllAsTouched(); return; }

    this.loading.set(true);
    this.errorMessage.set('');

    this.authService.login(this.loginForm.value)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.loading.set(false);
          // returnUrl was already validated in ngOnInit — safe to use directly.
          this.router.navigateByUrl(this.returnUrl).then(() => this.setFocusOnMainContent());
        },
        error: (error: HttpErrorResponse) => {
          this.loading.set(false);
          if (error.status === 401) {
            this.errorMessage.set('Invalid email or password. Please check your credentials and try again.');
          } else if (error.status === 429) {
            this.errorMessage.set('Too many login attempts. Please wait a few minutes and try again.');
          } else if (error.status === 0) {
            this.errorMessage.set('Unable to connect to the server. Please check your internet connection.');
          } else {
            this.errorMessage.set('An error occurred during login. Please try again later.');
          }
        }
      });
  }

  private setFocusOnMainContent(): void {
    setTimeout(() => {
      const heading = document.querySelector('h1, [role="main"], main');
      if (heading instanceof HTMLElement) {
        heading.setAttribute('tabindex', '-1');
        heading.focus();
      }
    }, 100);
  }

  get email() { return this.loginForm.get('email'); }
  get password() { return this.loginForm.get('password'); }
}
