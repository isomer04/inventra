import { Component, input, computed } from '@angular/core';

interface BadgeConfig {
  cssClass: string;
  label: string;
}

const STATUS_BADGE_MAP: Record<string, BadgeConfig> = {
  'in-stock':     { cssClass: 'status-badge--in-stock',     label: 'In Stock' },
  'low-stock':    { cssClass: 'status-badge--low-stock',    label: 'Low Stock' },
  'out-of-stock': { cssClass: 'status-badge--out-of-stock', label: 'Out of Stock' },
  'pending':      { cssClass: 'status-badge--pending',      label: 'Pending' },
  'processing':   { cssClass: 'status-badge--processing',   label: 'Processing' },
  'shipped':      { cssClass: 'status-badge--shipped',      label: 'Shipped' },
  'delivered':    { cssClass: 'status-badge--delivered',    label: 'Delivered' },
  'cancelled':    { cssClass: 'status-badge--cancelled',    label: 'Cancelled' },
  'active':       { cssClass: 'status-badge--in-stock',     label: 'Active' },
  'inactive':     { cssClass: 'status-badge--out-of-stock', label: 'Inactive' },
  'discontinued': { cssClass: 'status-badge--cancelled',    label: 'Discontinued' },
  'approved':     { cssClass: 'status-badge--delivered',    label: 'Approved' },
  'suspended':    { cssClass: 'status-badge--cancelled',    label: 'Suspended' },
};

const FALLBACK: BadgeConfig = { cssClass: 'status-badge--unknown', label: '' };

@Component({
  selector: 'app-status-badge',
  standalone: true,
  imports: [],
  template: `
    <span
      class="status-badge"
      [class]="'status-badge ' + config().cssClass"
      role="status"
      [attr.aria-label]="config().label || status()">
      {{ config().label || status() }}
    </span>
  `,
})
export class StatusBadgeComponent {
  status = input.required<string>();

  readonly config = computed(() => {
    const key = this.status().toLowerCase().replace(/_/g, '-').replace(/\s+/g, '-');
    return STATUS_BADGE_MAP[key] ?? { ...FALLBACK, label: this.status() };
  });
}
