import { Component, input, output, computed } from '@angular/core';
import { DecimalPipe } from '@angular/common';

@Component({
  selector: 'app-kpi-card',
  standalone: true,
  imports: [DecimalPipe],
  templateUrl: './kpi-card.html',
  styleUrl: './kpi-card.scss',
})
export class KpiCardComponent {
  label         = input.required<string>();
  value         = input<string | number | null>(null);
  icon          = input('bi-bar-chart');
  trend         = input<'up' | 'down' | 'neutral'>('neutral');
  changePercent = input<number | null>(null);
  loading       = input(false);
  error         = input<string | null>(null);

  retry = output<void>();

  readonly trendIcon = computed(() => {
    switch (this.trend()) {
      case 'up':   return 'bi-arrow-up-short';
      case 'down': return 'bi-arrow-down-short';
      default:     return 'bi-dash';
    }
  });

  readonly trendClass = computed(() => {
    switch (this.trend()) {
      case 'up':   return 'kpi-card__trend--up';
      case 'down': return 'kpi-card__trend--down';
      default:     return 'kpi-card__trend--neutral';
    }
  });
}
