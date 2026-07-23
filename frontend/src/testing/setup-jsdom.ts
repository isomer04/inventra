/**
 * Global jsdom polyfills used by every test file.
 *
 * Wired into angular.json via the unit-test builder's `setupFiles` option.
 */

// jsdom does not implement window.matchMedia. ThemeService and other
// components call it during construction, which crashes the TestBed factory.
// Provide a minimal stub that mimics the MediaQueryList interface.
if (typeof window !== 'undefined' && !window.matchMedia) {
  (window as Window & typeof globalThis).matchMedia = (query: string): MediaQueryList => ({
    matches: false,
    media: query,
    onchange: null,
    addListener: () => undefined,
    removeListener: () => undefined,
    addEventListener: () => undefined,
    removeEventListener: () => undefined,
    dispatchEvent: () => false,
  } as unknown as MediaQueryList);
}
