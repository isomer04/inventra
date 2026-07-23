import { Component, input, computed } from '@angular/core';
import { RouterModule } from '@angular/router';
import { InventoryItem } from '../../../models/inventory.model';

@Component({
  selector: 'app-low-stock-alerts',
  standalone: true,
  imports: [RouterModule],
  templateUrl: './low-stock-alerts.html',
  styleUrl: './low-stock-alerts.scss',
})
export class LowStockAlertsComponent {
  products = input<InventoryItem[]>([]);
  loading  = input(false);

  readonly alertItems = computed(() =>
    this.products().filter(p => p.quantityOnHand <= p.reorderPoint)
  );
}
