import { Component, input, ChangeDetectionStrategy } from '@angular/core';
import { NgTemplateOutlet } from '@angular/common';
import { RouterModule } from '@angular/router';

export type KpiVariant = 'primary' | 'success' | 'warning' | 'danger' | 'info' | 'secondary';

@Component({
  selector: 'app-kpi-tile',
  standalone: true,
  imports: [RouterModule, NgTemplateOutlet],
  templateUrl: './kpi-tile.html',
  styleUrl: './kpi-tile.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class KpiTileComponent {
  readonly label = input('');
  readonly value = input<string | number | null>(null);
  readonly subValue = input<string | null>(null);
  readonly icon = input('bi-bar-chart');
  readonly variant = input<KpiVariant>('primary');
  readonly loading = input(false);
  readonly link = input<string | null>(null);
  readonly badge = input<string | null>(null);
  readonly badgeVariant = input<KpiVariant>('warning');
}
