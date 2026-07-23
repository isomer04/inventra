import { Component, input, ChangeDetectionStrategy } from '@angular/core';
import { CurrencyPipe, DatePipe } from '@angular/common';
import { RouterModule } from '@angular/router';
import { Order, OrderStatus } from '../../../models/order.model';
import { statusConfig, StatusConfig } from '../../../models/order-status.helpers';

@Component({
  selector: 'app-recent-orders',
  standalone: true,
  imports: [RouterModule, CurrencyPipe, DatePipe],
  templateUrl: './recent-orders.html',
  styleUrl: './recent-orders.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RecentOrdersComponent {
  readonly orders = input<Order[]>([]);
  readonly loading = input(false);

  getStatusConfig(status: OrderStatus): StatusConfig {
    return statusConfig(status);
  }
}
