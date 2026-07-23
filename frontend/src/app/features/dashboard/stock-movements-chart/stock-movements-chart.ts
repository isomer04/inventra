import { Component, input, effect, signal, ChangeDetectionStrategy } from '@angular/core';
import { BaseChartDirective } from 'ng2-charts';
import { ChartData, ChartOptions } from 'chart.js';
import { MovementGroup } from '../../../models/report.model';

/** Bootstrap-aligned colours per movement type. */
const TYPE_COLORS: Record<string, string> = {
  RECEIPT:             '#198754',
  ADJUSTMENT:          '#fd7e14',
  RESERVATION:         '#0d6efd',
  RESERVATION_RELEASE: '#6610f2',
  DEDUCTION:           '#dc3545',
};

@Component({
  selector: 'app-stock-movements-chart',
  standalone: true,
  imports: [BaseChartDirective],
  templateUrl: './stock-movements-chart.html',
  styleUrl: './stock-movements-chart.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class StockMovementsChartComponent {
  readonly movements = input<MovementGroup[]>([]);
  readonly loading = input(false);

  chartData = signal<ChartData<'bar'>>({ labels: [], datasets: [] });

  readonly chartOptions: ChartOptions<'bar'> = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: {
      legend: { position: 'bottom', labels: { boxWidth: 12, font: { size: 11 } } },
      tooltip: {
        callbacks: {
          label: (ctx) => ` ${ctx.dataset.label}: ${ctx.parsed.y} units`,
        },
      },
    },
    scales: {
      x: {
        stacked: true,
        ticks: { font: { size: 11 } },
        grid: { display: false },
      },
      y: {
        stacked: true,
        beginAtZero: true,
        ticks: { precision: 0 },
        grid: { color: '#f1f3f5' },
      },
    },
  };

  constructor() {
    effect(() => {
      const rows = this.movements();
      if (!rows.length) {
        this.chartData.set({ labels: [], datasets: [] });
        return;
      }

      // Each row from groupBy=date_type has both .date and .type.
      // Build a (date → type → quantity) map for the stacked bar chart.
      const dateSet = new Set<string>();
      const typeSet = new Set<string>();
      const map = new Map<string, Map<string, number>>();

      for (const m of rows) {
        const dateKey = m.date ?? 'Unknown';
        const typeKey = m.type ?? 'Unknown';
        dateSet.add(dateKey);
        typeSet.add(typeKey);
        if (!map.has(dateKey)) map.set(dateKey, new Map());
        const byType = map.get(dateKey)!;
        byType.set(typeKey, (byType.get(typeKey) ?? 0) + Number(m.totalQuantity));
      }

      const dates = Array.from(dateSet).sort();
      const types = Array.from(typeSet);

      this.chartData.set({
        labels: dates,
        datasets: types.map((type) => ({
          label: type,
          data: dates.map((d) => map.get(d)?.get(type) ?? 0),
          backgroundColor: TYPE_COLORS[type] ?? '#6c757d',
          borderRadius: 2,
        })),
      });
    });
  }
}
