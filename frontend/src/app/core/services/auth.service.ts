import { Injectable, inject, PLATFORM_ID, signal, Signal } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject, Observable, tap, finalize, shareReplay, switchMap, catchError, of, map, throwError } from 'rxjs';
import { Router } from '@angular/router';
import { environment } from '../../../environments/environment';
import { LoginRequest, RegisterRequest, TokenResponse, RefreshRequest, LogoutRequest, JwtPayload, User, UserRole, UserStatus } from '../../models';

@Injectable({
  providedIn: 'root'
})
export class AuthService {
  private http = inject(HttpClient);
  private router = inject(Router);
  private platformId = inject(PLATFORM_ID);
  private isBrowser = isPlatformBrowser(this.platformId);
  
  private readonly API_URL = environment.apiUrl;
  private readonly ACCESS_TOKEN_KEY = 'access_token';
  private readonly REFRESH_TOKEN_KEY = 'refresh_token';
  
  private currentUserSubject = new BehaviorSubject<User | null>(null);
  public currentUser$ = this.currentUserSubject.asObservable();

  /**
   * Signal-based current user — preferred for components using Angular 21 signals.
   * Typed as Signal<User | null> (readonly view) so external callers cannot
   * call .set() directly; mutations go through setCurrentUser().
   */
  readonly currentUser: Signal<User | null> = signal<User | null>(null);

  // Private writable handle — only this service mutates it
  private readonly _currentUser = this.currentUser as ReturnType<typeof signal<User | null>>;
  
  // Prevent concurrent refresh token calls
  private refreshInFlight$: Observable<TokenResponse> | null = null;

  private setCurrentUser(user: User | null): void {
    this.currentUserSubject.next(user);
    this._currentUser.set(user);
  }

  constructor() {
    try {
      const token = this.getAccessToken();
      if (token) {
        const payload = this.decodeToken(token);
        if (payload && !this.isTokenExpired(payload)) {
          // JWT-derived user first so guards and role checks are correct
          // synchronously, then fill in the profile fields the token doesn't carry.
          this.setCurrentUser(this.createUserFromPayload(payload));
          this.scheduleProfileRefresh(payload.sub);
        } else {
          this.clearTokens();
        }
      }
    } catch (error) {
      console.error('Failed to initialize auth state:', error);
      this.clearTokens();
    }
  }

  /**
   * Fetch the full profile for a rehydrated session.
   *
   * <p>Deferred to a microtask: subscribing inside the constructor would run the
   * auth interceptor, which injects this very service, while it is still being
   * constructed — a circular dependency. A failure here is non-fatal; the
   * JWT-derived placeholder stays in place and the interceptor handles a dead
   * session on the next real request.
   */
  private scheduleProfileRefresh(userId: string): void {
    if (!this.isBrowser) return;
    queueMicrotask(() => {
      this.fetchProfile(userId).subscribe(user => {
        if (user) {
          this.setCurrentUser(user);
        }
      });
    });
  }

  /** Fetch a user profile, collapsing any failure to `null` so callers can fall back. */
  private fetchProfile(userId: string): Observable<User | null> {
    return this.http.get<User>(`${this.API_URL}/users/${userId}`).pipe(
      catchError(() => of(null))
    );
  }

  /**
   * Populate the current user after a successful auth call, then pass the original
   * response through untouched.
   *
   * <p>The JWT carries no email/firstName/lastName, so the full profile is fetched;
   * if that fetch fails the JWT-derived user is used instead.
   */
  private hydrateCurrentUser<T>(response: T): Observable<T> {
    const token = this.getAccessToken();
    const payload = token ? this.decodeToken(token) : null;
    if (!payload) {
      return of(response);
    }
    if (!payload.sub) {
      this.setCurrentUser(this.createUserFromPayload(payload));
      return of(response);
    }
    return this.fetchProfile(payload.sub).pipe(
      tap(user => this.setCurrentUser(user ?? this.createUserFromPayload(payload))),
      map(() => response)
    );
  }

  register(request: RegisterRequest): Observable<TokenResponse> {
    return this.http.post<TokenResponse>(`${this.API_URL}/auth/register`, request).pipe(
      tap(response => {
        this.storeTokens(response);
      }),
      switchMap(response => this.hydrateCurrentUser(response))
    );
  }

  login(request: LoginRequest): Observable<TokenResponse> {
    return this.http.post<TokenResponse>(`${this.API_URL}/auth/login`, request).pipe(
      tap(response => {
        this.storeTokens(response);
      }),
      switchMap(response => this.hydrateCurrentUser(response))
    );
  }

  logout(): Observable<void> {
    const refreshToken = this.getRefreshToken();
    if (refreshToken) {
      const request: LogoutRequest = { refreshToken };
      return this.http.post<void>(`${this.API_URL}/auth/logout`, request).pipe(
        finalize(() => {
          this.clearTokens();
          this.setCurrentUser(null);
          this.router.navigate(['/login']);
        })
      );
    } else {
      this.clearTokens();
      this.setCurrentUser(null);
      this.router.navigate(['/login']);
      return new Observable(observer => {
        observer.next();
        observer.complete();
      });
    }
  }

  /**
   * Drop all client-side auth state without calling the server.
   *
   * <p>Used on paths where the session is already known to be dead (e.g. a failed
   * refresh), where calling `logout()` would trigger a pointless — and circular —
   * HTTP request from inside the auth interceptor.
   */
  clearSession(): void {
    this.clearTokens();
    this.setCurrentUser(null);
  }

  refreshToken(): Observable<TokenResponse> {
    if (this.refreshInFlight$) {
      return this.refreshInFlight$;
    }

    const refreshToken = this.getRefreshToken();
    if (!refreshToken) {
      // Returned as an error stream rather than thrown synchronously: a synchronous
      // throw escapes the interceptor's catchError, so the redirect-to-login cleanup
      // never ran and the caller saw a raw exception instead of a login redirect.
      return throwError(() => new Error('No refresh token available'));
    }

    const request: RefreshRequest = { refreshToken };
    this.refreshInFlight$ = this.http.post<TokenResponse>(`${this.API_URL}/auth/refresh`, request).pipe(
      tap(response => {
        this.storeTokens(response);
        const payload = this.decodeToken(response.accessToken);
        if (payload) {
          // Keep the already-resolved profile if it belongs to the same user —
          // the JWT carries no email/firstName/lastName, so rebuilding blindly
          // would blank the identity display on every silent token refresh.
          const existing = this.currentUserSubject.value;
          if (existing && existing.id === payload.sub) {
            const fromToken = this.createUserFromPayload(payload);
            this.setCurrentUser({
              ...existing,
              tenantId: fromToken.tenantId,
              role: fromToken.role
            });
          } else {
            this.setCurrentUser(this.createUserFromPayload(payload));
          }
        }
      }),
      finalize(() => {
        this.refreshInFlight$ = null;
      }),
      shareReplay(1)
    );
    
    return this.refreshInFlight$;
  }

  isAuthenticated(): boolean {
    const token = this.getAccessToken();
    if (!token) {
      return false;
    }
    
    const payload = this.decodeToken(token);
    return payload !== null && !this.isTokenExpired(payload);
  }

  hasRole(role: UserRole): boolean {
    const user = this.currentUserSubject.value;
    return user?.role === role;
  }

  hasAnyRole(roles: UserRole[]): boolean {
    const user = this.currentUserSubject.value;
    return user ? roles.includes(user.role) : false;
  }

  getAccessToken(): string | null {
    return this.isBrowser ? sessionStorage.getItem(this.ACCESS_TOKEN_KEY) : null;
  }

  getRefreshToken(): string | null {
    return this.isBrowser ? sessionStorage.getItem(this.REFRESH_TOKEN_KEY) : null;
  }

  private storeTokens(response: TokenResponse): void {
    if (!this.isBrowser) return;
    sessionStorage.setItem(this.ACCESS_TOKEN_KEY, response.accessToken);
    sessionStorage.setItem(this.REFRESH_TOKEN_KEY, response.refreshToken);
  }

  private clearTokens(): void {
    if (!this.isBrowser) return;
    sessionStorage.removeItem(this.ACCESS_TOKEN_KEY);
    sessionStorage.removeItem(this.REFRESH_TOKEN_KEY);
  }

  private decodeToken(token: string): JwtPayload | null {
    try {
      const parts = token.split('.');
      if (parts.length !== 3) {
        return null;
      }
      
      const payloadPart = parts[1];
      if (!payloadPart) {
        return null;
      }
      
      // Convert base64url to base64
      let payload = payloadPart;
      payload = payload.replace(/-/g, '+').replace(/_/g, '/');
      
      // Add padding if needed
      while (payload.length % 4 !== 0) {
        payload += '=';
      }
      
      const decoded = atob(payload);
      const parsed: unknown = JSON.parse(decoded);
      
      if (
        parsed &&
        typeof parsed === 'object' &&
        'sub' in parsed &&
        'tenantId' in parsed &&
        'tenantSlug' in parsed &&
        'roles' in parsed &&
        'iat' in parsed &&
        'exp' in parsed &&
        typeof parsed.sub === 'string' &&
        typeof parsed.tenantId === 'string' &&
        typeof parsed.tenantSlug === 'string' &&
        Array.isArray(parsed.roles) &&
        typeof parsed.iat === 'number' &&
        typeof parsed.exp === 'number'
      ) {
        return parsed as JwtPayload;
      }
      
      return null;
    } catch (error) {
      if (!environment.production) {
        console.error('Error decoding token:', error);
      }
      return null;
    }
  }

  private isTokenExpired(payload: JwtPayload): boolean {
    const now = Math.floor(Date.now() / 1000);
    return payload.exp < now;
  }

  private createUserFromPayload(payload: JwtPayload): User {
    const role = payload.roles.length > 0 ? payload.roles[0] : UserRole.VIEWER;
    
    return {
      id: payload.sub,
      tenantId: payload.tenantId,
      email: '', // Not available in JWT
      firstName: '', // Not available in JWT
      lastName: '', // Not available in JWT
      role,
      status: UserStatus.ACTIVE,
      createdAt: '',
      updatedAt: ''
    };
  }
}

