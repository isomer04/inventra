import { Component } from '@angular/core';
import { RouterModule } from '@angular/router';

@Component({
  selector: 'app-reports-dashboard',
  standalone: true,
  imports: [RouterModule],
  templateUrl: './reports-dashboard.html',
  styleUrl: './reports-dashboard.scss'
})
export class ReportsDashboardComponent {
  reports = [
    {
      title: 'Inventory Summary',
      description: 'View total SKUs, stock value, and low-stock items',
      icon: '📦',
      route: '/reports/inventory'
    },
    {
      title: 'Stock Movements',
      description: 'Analyze stock movements by type and date',
      icon: '↔',
      route: '/reports/stock-movements'
    },
    {
      title: 'Order Summary',
      description: 'View orders by status, GMV, and fulfillment time',
      icon: '🛒',
      route: '/reports/orders'
    },
    {
      title: 'Top Products',
      description: 'See best-selling products by quantity and revenue',
      icon: '🏆',
      route: '/reports/top-products'
    }
  ];
}
