import {
  Component,
  inject,
  signal,
  input,
  output,
  DestroyRef,
  ElementRef,
  PLATFORM_ID,
} from '@angular/core';
import { isPlatformBrowser, DOCUMENT, UpperCasePipe } from '@angular/common';
import { RouterModule } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';

interface NavSection {
  label: string;
  items: NavItem[];
}

interface NavItem {
  label: string;
  icon: string;
  route: string;
}

const NAV_SECTIONS: NavSection[] = [
  {
    label: 'Overview',
    items: [{ label: 'Dashboard', icon: 'bi-speedometer2', route: '/dashboard' }],
  },
  {
    label: 'Catalog',
    items: [
      { label: 'Products',  icon: 'bi-box',    route: '/products' },
      { label: 'Inventory', icon: 'bi-boxes',  route: '/inventory' },
    ],
  },
  {
    label: 'Commerce',
    items: [
      { label: 'Orders',    icon: 'bi-cart3',  route: '/orders' },
      { label: 'Customers', icon: 'bi-people', route: '/customers' },
    ],
  },
  {
    label: 'Analytics',
    items: [{ label: 'Reports', icon: 'bi-graph-up', route: '/reports' }],
  },
];

@Component({
  selector: 'app-sidebar',
  standalone: true,
  imports: [RouterModule, UpperCasePipe],
  templateUrl: './sidebar.html',
  styleUrl: './sidebar.scss',
})
export class SidebarComponent {
  private readonly authService  = inject(AuthService);
  private readonly el           = inject(ElementRef);
  private readonly doc          = inject(DOCUMENT);
  private readonly platformId   = inject(PLATFORM_ID);
  private readonly destroyRef   = inject(DestroyRef);

  collapsed      = input<boolean>(false);
  overlayClose   = output<void>();
  toggleCollapse = output<void>();

  readonly navSections = NAV_SECTIONS;
  readonly userDropdownOpen = signal(false);

  readonly currentUser = this.authService.currentUser;

  constructor() {
    if (isPlatformBrowser(this.platformId)) {
      const handler = (e: Event) => {
        if (this.userDropdownOpen() && !this.el.nativeElement.contains(e.target)) {
          this.userDropdownOpen.set(false);
        }
      };
      this.doc.addEventListener('click', handler);
      this.destroyRef.onDestroy(() => this.doc.removeEventListener('click', handler));
    }
  }

  toggleUserDropdown(): void {
    this.userDropdownOpen.update(v => !v);
  }

  logout(): void {
    this.authService.logout().subscribe();
  }

  onToggle(): void {
    this.toggleCollapse.emit();
  }

  onOverlayClick(): void {
    this.overlayClose.emit();
  }
}
