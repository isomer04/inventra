import { Component, input, computed } from '@angular/core';
import { BaseChartDirective } from 'ng2-charts';
import { ChartData, ChartOptions } from 'chart.js';

export interface StockHealthSummary {
  inStock: number;
  lowStock: number;
  outOfStock: number;
}

const BASE_DATASET = {
  backgroundColor: ['#D1FAE5', '#FEF3C7', '#FEE2E2'],
  borderColor:     ['#065F46', '#92400E', '#991B1B'],
  borderWidth: 1,
} as const;

@Component({
  selector: 'app-stock-health-chart',
  standalone: true,
  imports: [BaseChartDirective],
  templateUrl: './stock-health-chart.html',
  styleUrl: './stock-health-chart.scss',
})
export class StockHealthChartComponent {
  data    = input<StockHealthSummary | null>(null);
  loading = input(false);

  readonly total = computed(() => {
    const d = this.data();
    return d ? d.inStock + d.lowStock + d.outOfStock : 0;
  });

  readonly ariaLabel = computed(() => {
    const d = this.data();
    if (!d) return 'Stock health chart — no data';
    return `Stock health: ${d.inStock} in stock, ${d.lowStock} low stock, ${d.outOfStock} out of stock out of ${this.total()} total`;
  });

  /**
   * chartData is a computed signal so the [data] template binding re-evaluates
   * whenever data() changes — no effect() mutation needed, no stale chart.
   */
  readonly chartData = computed((): ChartData<'doughnut'> => {
    const d = this.data();
    return {
      labels: ['In Stock', 'Low Stock', 'Out of Stock'],
      datasets: [{
        ...BASE_DATASET,
        data: d ? [d.inStock, d.lowStock, d.outOfStock] : [0, 0, 0],
      }],
    };
  });

  readonly chartOptions: ChartOptions<'doughnut'> = {
    responsive: true,
    maintainAspectRatio: false,
    cutout: '65%',
    plugins: {
      legend: { position: 'bottom', labels: { boxWidth: 12, padding: 16 } },
      tooltip: { enabled: true },
    },
  };
}
