import { Component, input, effect, signal, ChangeDetectionStrategy } from '@angular/core';
import { BaseChartDirective } from 'ng2-charts';
import { ChartData, ChartOptions } from 'chart.js';
import { TopProduct } from '../../../models/report.model';

@Component({
  selector: 'app-top-products-chart',
  standalone: true,
  imports: [BaseChartDirective],
  templateUrl: './top-products-chart.html',
  styleUrl: './top-products-chart.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TopProductsChartComponent {
  readonly products = input<TopProduct[]>([]);
  readonly loading = input(false);

  chartData = signal<ChartData<'bar'>>({ labels: [], datasets: [] });

  readonly chartOptions: ChartOptions<'bar'> = {
    indexAxis: 'y',
    responsive: true,
    maintainAspectRatio: false,
    plugins: {
      legend: { display: false },
      tooltip: {
        callbacks: {
          label: (ctx) => ` ${ctx.parsed.x} units sold`,
        },
      },
    },
    scales: {
      x: {
        beginAtZero: true,
        ticks: { precision: 0 },
        grid: { color: '#f1f3f5' },
      },
      y: {
        ticks: { font: { size: 12 } },
        grid: { display: false },
      },
    },
  };

  constructor() {
    effect(() => {
      const prods = this.products();
      if (!prods.length) {
        this.chartData.set({ labels: [], datasets: [] });
        return;
      }
      this.chartData.set({
        labels: prods.map((p) => p.name),
        datasets: [
          {
            label: 'Units Sold',
            data: prods.map((p) => p.totalQuantitySold),
            backgroundColor: '#0d6efd',
            borderRadius: 4,
          },
        ],
      });
    });
  }
}
