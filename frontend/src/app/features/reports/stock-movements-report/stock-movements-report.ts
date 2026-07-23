import { Component, OnInit, inject, DestroyRef, signal } from '@angular/core';
import { RouterModule } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { ReportService } from '../../../core/services/report.service';
import { ToastService } from '../../../core/services/toast.service';
import { StockMovementReport } from '../../../models';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize } from 'rxjs/operators';

@Component({
  selector: 'app-stock-movements-report',
  standalone: true,
  imports: [RouterModule, FormsModule],
  templateUrl: './stock-movements-report.html',
  styleUrl: './stock-movements-report.scss'
})
export class StockMovementsReportComponent implements OnInit {
  private reportService = inject(ReportService);
  private toastService = inject(ToastService);
  private destroyRef = inject(DestroyRef);

  report = signal<StockMovementReport | null>(null);
  loading = signal(true);

  startDate = '';
  endDate = '';
  groupBy: 'type' | 'date' = 'type';

  ngOnInit(): void {
    this.loadReport();
  }

  loadReport(): void {
    this.loading.set(true);
    const params: Record<string, string> = { groupBy: this.groupBy };
    if (this.startDate) params['startDate'] = this.startDate;
    if (this.endDate) params['endDate'] = this.endDate;

    this.reportService.getStockMovements(params)
      .pipe(takeUntilDestroyed(this.destroyRef), finalize(() => this.loading.set(false)))
      .subscribe({
        next: (report) => { this.report.set(report); },
        error: (error) => {
          console.error('Failed to load stock movements report:', error);
          this.toastService.error('Failed to load report');
        }
      });
  }
}

