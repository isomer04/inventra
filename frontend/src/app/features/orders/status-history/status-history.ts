import { Component, input } from '@angular/core';
import { NgClass, DatePipe } from '@angular/common';
import { OrderStatusHistory, OrderStatus } from '../../../models';
import { statusConfig } from '../../../models/order-status.helpers';

@Component({
  selector: 'app-status-history',
  standalone: true,
  imports: [NgClass, DatePipe],
  templateUrl: './status-history.html',
  styleUrl: './status-history.scss'
})
export class StatusHistoryComponent {
  history = input<OrderStatusHistory[]>([]);

  getStatusBadgeClass(status: OrderStatus): string {
    return statusConfig(status).badgeClass;
  }

  getStatusIcon(status: OrderStatus): string {
    switch (status) {
      case OrderStatus.DRAFT:
        return 'bi-file-earmark';
      case OrderStatus.SUBMITTED:
        return 'bi-send';
      case OrderStatus.APPROVED:
        return 'bi-check-circle';
      case OrderStatus.PICKING:
        return 'bi-box';
      case OrderStatus.SHIPPED:
        return 'bi-truck';
      case OrderStatus.DELIVERED:
        return 'bi-check-circle-fill';
      case OrderStatus.REJECTED:
        return 'bi-x-circle';
      case OrderStatus.CANCELLED:
        return 'bi-x-octagon';
      default:
        return 'bi-circle';
    }
  }
}
