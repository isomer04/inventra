import { Injectable, signal, inject, PLATFORM_ID } from '@angular/core';
import { isPlatformBrowser, DOCUMENT } from '@angular/common';

export type Theme = 'light' | 'dark';

@Injectable({ providedIn: 'root' })
export class ThemeService {
  private readonly STORAGE_KEY = 'inventra-theme';
  private readonly htmlEl = inject(DOCUMENT).documentElement;
  private readonly platformId = inject(PLATFORM_ID);

  // Private writable signal — mutated only through applyTheme()
  private readonly _theme = signal<Theme>('light');

  /**
   * Read-only signal — consumers can read the current theme but cannot call
   * .set() directly. Use setTheme() for intentional user-driven changes.
   */
  readonly theme = this._theme.asReadonly();

  constructor() {
    // Guard all browser-only APIs — safe for SSR / pre-rendering
    if (!isPlatformBrowser(this.platformId)) {
      return;
    }

    this.initTheme();

    // React to OS preference changes live — only fires when no stored preference
    window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change', e => {
      if (!localStorage.getItem(this.STORAGE_KEY)) {
        this.applyTheme(e.matches ? 'dark' : 'light');
      }
    });
  }

  /** Sets a user preference and persists it. */
  setTheme(theme: Theme): void {
    if (isPlatformBrowser(this.platformId)) {
      localStorage.setItem(this.STORAGE_KEY, theme);
    }
    this.applyTheme(theme);
  }

  private initTheme(): void {
    const stored = localStorage.getItem(this.STORAGE_KEY);
    if (stored === 'light' || stored === 'dark') {
      this.applyTheme(stored);
    } else {
      const prefersDark = window.matchMedia('(prefers-color-scheme: dark)').matches;
      this.applyTheme(prefersDark ? 'dark' : 'light');
    }
  }

  private applyTheme(theme: Theme): void {
    this.htmlEl.setAttribute('data-theme', theme);
    this._theme.set(theme);
  }
}
