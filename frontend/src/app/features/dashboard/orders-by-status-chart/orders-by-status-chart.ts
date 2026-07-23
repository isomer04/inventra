import { Component, input, effect, signal, ChangeDetectionStrategy } from '@angular/core';
import { BaseChartDirective } from 'ng2-charts';
import { ChartData, ChartOptions } from 'chart.js';
import { OrderStatusGroup } from '../../../models/report.model';
import { statusConfig } from '../../../models/order-status.helpers';

@Component({
  selector: 'app-orders-by-status-chart',
  standalone: true,
  imports: [BaseChartDirective],
  templateUrl: './orders-by-status-chart.html',
  styleUrl: './orders-by-status-chart.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class OrdersByStatusChartComponent {
  readonly ordersByStatus = input<OrderStatusGroup[]>([]);
  readonly loading = input(false);

  chartData = signal<ChartData<'doughnut'>>({ labels: [], datasets: [] });

  readonly chartOptions: ChartOptions<'doughnut'> = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: {
      legend: {
        position: 'right',
        labels: { boxWidth: 12, font: { size: 12 } },
      },
      tooltip: {
        callbacks: {
          label: (ctx) => ` ${ctx.label}: ${ctx.parsed} orders`,
        },
      },
    },
  };

  constructor() {
    effect(() => {
      const active = this.ordersByStatus().filter((g) => g.count > 0);
      if (!active.length) {
        this.chartData.set({ labels: [], datasets: [] });
        return;
      }
      this.chartData.set({
        labels: active.map((g) => g.status),
        datasets: [
          {
            data: active.map((g) => g.count),
            backgroundColor: active.map((g) => statusConfig(g.status).color),
            borderWidth: 2,
            borderColor: '#fff',
          },
        ],
      });
    });
  }
}
