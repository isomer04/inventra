import {
  Component,
  inject,
  signal,
  computed,
  output,
  DestroyRef,
  ElementRef,
  PLATFORM_ID,
} from '@angular/core';
import { isPlatformBrowser, DOCUMENT } from '@angular/common';
import { RouterModule } from '@angular/router';
import { toSignal } from '@angular/core/rxjs-interop';
import { AuthService } from '../../../core/services/auth.service';
import { BreadcrumbService } from '../../../core/services/breadcrumb.service';

@Component({
  selector: 'app-top-bar',
  standalone: true,
  imports: [RouterModule],
  templateUrl: './top-bar.html',
  styleUrl: './top-bar.scss',
})
export class TopBarComponent {
  private readonly authService       = inject(AuthService);
  private readonly breadcrumbService = inject(BreadcrumbService);
  private readonly el                = inject(ElementRef);
  private readonly doc               = inject(DOCUMENT);
  private readonly platformId        = inject(PLATFORM_ID);
  private readonly destroyRef        = inject(DestroyRef);

  menuToggle = output<void>();

  readonly breadcrumbs = toSignal(this.breadcrumbService.breadcrumbs$, { initialValue: [] });

  readonly dropdownOpen = signal(false);

  readonly currentUser = this.authService.currentUser;

  readonly initials = computed(() => {
    const user = this.currentUser();
    if (!user) return '';
    const f = user.firstName?.[0] ?? '';
    const l = user.lastName?.[0] ?? '';
    const fromName = (f + l).toUpperCase();
    if (fromName) return fromName;
    const e = user.email?.[0] ?? '';
    return e ? e.toUpperCase() : '?';
  });

  constructor() {
    if (isPlatformBrowser(this.platformId)) {
      const clickHandler = (e: Event) => {
        if (this.dropdownOpen() && !this.el.nativeElement.contains(e.target)) {
          this.dropdownOpen.set(false);
        }
      };
      const keyHandler = (e: KeyboardEvent) => {
        if (e.key === 'Escape' && this.dropdownOpen()) {
          this.dropdownOpen.set(false);
        }
      };
      this.doc.addEventListener('click', clickHandler);
      this.doc.addEventListener('keydown', keyHandler);
      this.destroyRef.onDestroy(() => {
        this.doc.removeEventListener('click', clickHandler);
        this.doc.removeEventListener('keydown', keyHandler);
      });
    }
  }

  toggleDropdown(): void {
    this.dropdownOpen.update(v => !v);
  }

  logout(): void {
    this.authService.logout().subscribe();
  }

  onMenuClick(): void {
    this.menuToggle.emit();
  }
}
