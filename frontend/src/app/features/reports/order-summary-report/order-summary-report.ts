import { Component, OnInit, inject, DestroyRef, signal, computed } from '@angular/core';
import { RouterModule } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { CurrencyPipe, DecimalPipe } from '@angular/common';
import { ReportService } from '../../../core/services/report.service';
import { ToastService } from '../../../core/services/toast.service';
import { OrderSummaryReport } from '../../../models';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize } from 'rxjs/operators';

@Component({
  selector: 'app-order-summary-report',
  standalone: true,
  imports: [RouterModule, FormsModule, CurrencyPipe, DecimalPipe],
  templateUrl: './order-summary-report.html',
  styleUrl: './order-summary-report.scss'
})
export class OrderSummaryReportComponent implements OnInit {
  private reportService = inject(ReportService);
  private toastService = inject(ToastService);
  private destroyRef = inject(DestroyRef);

  report = signal<OrderSummaryReport | null>(null);
  loading = signal(true);

  startDate = '';
  endDate = '';

  ngOnInit(): void {
    this.loadReport();
  }

  loadReport(): void {
    this.loading.set(true);
    const params: Record<string, string> = {};
    if (this.startDate) params['startDate'] = this.startDate;
    if (this.endDate) params['endDate'] = this.endDate;

    this.reportService.getOrderSummary(params)
      .pipe(takeUntilDestroyed(this.destroyRef), finalize(() => this.loading.set(false)))
      .subscribe({
        next: (report) => { this.report.set(report); },
        error: (error) => {
          console.error('Failed to load order summary:', error);
          this.toastService.error('Failed to load report');
        }
      });
  }

  ordersByStatusArray = computed(() => {
    const r = this.report();
    if (!r) return [];
    return r.ordersByStatus;
  });

  averageFulfillmentTimeHours = computed<number | null>(() => this.report()?.averageFulfillmentTimeHours ?? null);
}

