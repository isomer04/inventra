import { Component, signal, computed, effect, inject, DestroyRef, PLATFORM_ID } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';
import { RouterModule } from '@angular/router';
import { SidebarComponent } from '../sidebar/sidebar';
import { TopBarComponent } from '../top-bar/top-bar';

@Component({
  selector: 'app-main-layout',
  standalone: true,
  imports: [RouterModule, SidebarComponent, TopBarComponent],
  templateUrl: './main-layout.html',
  styleUrl: './main-layout.scss',
})
export class MainLayoutComponent {
  private readonly platformId = inject(PLATFORM_ID);
  private readonly destroyRef = inject(DestroyRef);

  readonly sidebarCollapsed = signal(false);
  readonly mobileMenuOpen   = signal(false);

  private readonly windowWidth = signal(
    isPlatformBrowser(this.platformId) ? window.innerWidth : 1280
  );

  readonly isMobile = computed(() => this.windowWidth() < 768);

  constructor() {
    if (isPlatformBrowser(this.platformId)) {
      // Replace @HostListener('window:resize') — cleaned up via DestroyRef
      const handler = () => this.windowWidth.set(window.innerWidth);
      window.addEventListener('resize', handler);
      this.destroyRef.onDestroy(() => window.removeEventListener('resize', handler));
    }

    // When the viewport becomes non-mobile, close the off-canvas overlay
    effect(() => {
      if (!this.isMobile()) {
        this.mobileMenuOpen.set(false);
      }
    });
  }

  toggleSidebar(): void {
    if (this.isMobile()) {
      this.mobileMenuOpen.update(v => !v);
    } else {
      this.sidebarCollapsed.update(v => !v);
    }
  }

  closeMobileMenu(): void {
    this.mobileMenuOpen.set(false);
  }
}
