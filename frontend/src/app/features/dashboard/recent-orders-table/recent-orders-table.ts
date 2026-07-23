import { Component, input, computed } from '@angular/core';
import { RouterModule } from '@angular/router';
import { DatePipe, CurrencyPipe } from '@angular/common';
import { Order } from '../../../models/order.model';
import { StatusBadgeComponent } from '../../../shared/components/status-badge/status-badge';

@Component({
  selector: 'app-recent-orders-table',
  standalone: true,
  imports: [RouterModule, DatePipe, CurrencyPipe, StatusBadgeComponent],
  templateUrl: './recent-orders-table.html',
  styleUrl: './recent-orders-table.scss',
})
export class RecentOrdersTableComponent {
  orders = input<Order[]>([]);

  readonly displayOrders = computed(() => this.orders().slice(0, 5));
}
