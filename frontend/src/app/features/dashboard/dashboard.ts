import {
  Component,
  OnInit,
  inject,
  signal,
  computed,
  ChangeDetectionStrategy,
  DestroyRef,
} from '@angular/core';
import { RouterModule } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';

import { DashboardService } from '../../core/services/dashboard.service';
import { DashboardOverview } from '../../models/dashboard.model';

import { KpiCardComponent } from './kpi-card/kpi-card';
import { StockHealthChartComponent, StockHealthSummary } from './stock-health-chart/stock-health-chart';
import { RecentOrdersTableComponent } from './recent-orders-table/recent-orders-table';
import { LowStockAlertsComponent } from './low-stock-alerts/low-stock-alerts';

const currencyFmt = new Intl.NumberFormat('en-US', {
  style: 'currency',
  currency: 'USD',
  maximumFractionDigits: 0,
});

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [
    RouterModule,
    KpiCardComponent,
    StockHealthChartComponent,
    RecentOrdersTableComponent,
    LowStockAlertsComponent,
  ],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DashboardComponent implements OnInit {
  private dashboardService = inject(DashboardService);
  private destroyRef = inject(DestroyRef);

  loading = signal(true);
  error = signal<string | null>(null);
  data = signal<DashboardOverview | null>(null);

  readonly totalProducts = computed(() => {
    const v = this.data()?.totalProducts;
    return v != null ? v.toLocaleString() : null;
  });

  readonly inventoryValue = computed(() => {
    const v = this.data()?.inventorySummary?.totalStockValue;
    return v != null ? currencyFmt.format(v) : null;
  });

  readonly openOrders = computed(() => {
    const v = this.data()?.pendingOrderCount;
    return v != null ? v.toLocaleString() : null;
  });

  readonly totalCustomers = computed(() => {
    const v = this.data()?.totalCustomers;
    return v != null ? v.toLocaleString() : null;
  });

  // NOTE: outOfStock is derived from lowStockItems (quantityOnHand === 0).
  // If the backend's /low-stock endpoint excludes zero-quantity items
  // (i.e. filters quantityOnHand > 0), outOfStock will always be 0 and the
  // in-stock segment will be inflated. Verify the backend contract; ideally
  // the API should return a dedicated StockHealthSummary with all three counts.

  readonly stockHealthSummary = computed((): StockHealthSummary | null => {
    const items = this.data()?.lowStockItems;
    const summary = this.data()?.inventorySummary;
    if (!summary) return null;

    const lowStock = items?.filter(i => i.quantityOnHand > 0 && i.quantityOnHand <= i.reorderPoint).length ?? 0;
    const outOfStock = items?.filter(i => i.quantityOnHand === 0).length ?? 0;
    const total = summary.totalSkus ?? 0;
    const inStock = Math.max(0, total - lowStock - outOfStock);

    return { inStock, lowStock, outOfStock };
  });

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.error.set(null);

    this.dashboardService
      .loadOverview()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (overview) => {
          this.data.set(overview);
          this.loading.set(false);
        },
        error: (err) => {
          console.error('Dashboard load failed', err);
          this.error.set('Failed to load dashboard data. Please try again.');
          this.loading.set(false);
        },
      });
  }
}
