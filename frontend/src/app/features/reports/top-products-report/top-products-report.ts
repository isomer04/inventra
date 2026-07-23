import { Component, OnInit, inject, DestroyRef, signal } from '@angular/core';
import { RouterModule } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { CurrencyPipe } from '@angular/common';
import { ReportService } from '../../../core/services/report.service';
import { ToastService } from '../../../core/services/toast.service';
import { TopProductsReport } from '../../../models';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize } from 'rxjs/operators';

@Component({
  selector: 'app-top-products-report',
  standalone: true,
  imports: [RouterModule, FormsModule, CurrencyPipe],
  templateUrl: './top-products-report.html',
  styleUrl: './top-products-report.scss'
})
export class TopProductsReportComponent implements OnInit {
  private reportService = inject(ReportService);
  private toastService = inject(ToastService);
  private destroyRef = inject(DestroyRef);

  report = signal<TopProductsReport | null>(null);
  loading = signal(true);

  days: 30 | 60 | 90 = 30;
  limit = 10;

  ngOnInit(): void {
    this.loadReport();
  }

  loadReport(): void {
    this.loading.set(true);
    this.reportService.getTopProducts({ days: this.days, limit: this.limit })
      .pipe(takeUntilDestroyed(this.destroyRef), finalize(() => this.loading.set(false)))
      .subscribe({
        next: (report) => { this.report.set(report); },
        error: (error) => {
          console.error('Failed to load top products:', error);
          this.toastService.error('Failed to load report');
        }
      });
  }
}

