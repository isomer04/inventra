import { Component, ChangeDetectionStrategy } from '@angular/core';
import { RouterModule } from '@angular/router';

interface QuickAction {
  label: string;
  icon: string;
  route: string;
  ariaLabel: string;
}

@Component({
  selector: 'app-quick-actions',
  standalone: true,
  imports: [RouterModule],
  templateUrl: './quick-actions.html',
  styleUrl: './quick-actions.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class QuickActionsComponent {
  readonly actions: QuickAction[] = [
    { label: 'New Order',     icon: 'bi-cart-plus',        route: '/orders/new',  ariaLabel: 'Create a new order' },
    { label: 'Receive Stock', icon: 'bi-box-arrow-in-down', route: '/inventory',   ariaLabel: 'Go to inventory to receive stock' },
    { label: 'Add Product',   icon: 'bi-plus-square',      route: '/products/new', ariaLabel: 'Add a new product' },
    { label: 'View Reports',  icon: 'bi-graph-up',         route: '/reports',     ariaLabel: 'View reports and analytics' },
  ];
}
