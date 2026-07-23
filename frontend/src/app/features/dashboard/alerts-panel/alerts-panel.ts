import { Component, input, computed, ChangeDetectionStrategy } from '@angular/core';
import { RouterModule } from '@angular/router';
import { InventoryItem } from '../../../models/inventory.model';
import { Order } from '../../../models/order.model';

@Component({
  selector: 'app-alerts-panel',
  standalone: true,
  imports: [RouterModule],
  templateUrl: './alerts-panel.html',
  styleUrl: './alerts-panel.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AlertsPanelComponent {
  readonly lowStockItems = input<InventoryItem[]>([]);
  /** Pre-fetched SUBMITTED orders from a dedicated API call — not derived from recentOrders. */
  readonly awaitingApprovalOrders = input<Order[]>([]);
  /** Pre-fetched APPROVED orders from a dedicated API call — not derived from recentOrders. */
  readonly readyToShipOrders = input<Order[]>([]);
  readonly loading = input(false);

  readonly hasAlerts = computed(
    () =>
      this.lowStockItems().length > 0 ||
      this.awaitingApprovalOrders().length > 0 ||
      this.readyToShipOrders().length > 0,
  );
}
