import { Component, OnInit, inject, DestroyRef, signal, computed } from '@angular/core';
import { RouterModule } from '@angular/router';
import { CurrencyPipe } from '@angular/common';
import { ReportService } from '../../../core/services/report.service';
import { ToastService } from '../../../core/services/toast.service';
import { InventorySummary } from '../../../models';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize } from 'rxjs/operators';

@Component({
  selector: 'app-inventory-summary',
  standalone: true,
  imports: [RouterModule, CurrencyPipe],
  templateUrl: './inventory-summary.html',
  styleUrl: './inventory-summary.scss'
})
export class InventorySummaryComponent implements OnInit {
  private reportService = inject(ReportService);
  private toastService = inject(ToastService);
  private destroyRef = inject(DestroyRef);

  report = signal<InventorySummary | null>(null);
  loading = signal(true);

  ngOnInit(): void {
    this.loadReport();
  }

  loadReport(): void {
    this.loading.set(true);
    this.reportService.getInventorySummary()
      .pipe(takeUntilDestroyed(this.destroyRef), finalize(() => this.loading.set(false)))
      .subscribe({
        next: (report) => { this.report.set(report); },
        error: (error) => {
          console.error('Failed to load inventory summary:', error);
          this.toastService.error('Failed to load inventory summary');
        }
      });
  }

  totalQuantityAvailable = computed<number>(() => {
    const r = this.report();
    if (!r) return 0;
    return r.totalQuantityAvailable ?? (r.totalQuantityOnHand - r.totalQuantityReserved);
  });

  stockUtilization = computed<number>(() => {
    const r = this.report();
    if (!r) return 0;
    const total = r.totalQuantityOnHand;
    const available = this.totalQuantityAvailable();
    return total > 0 ? ((total - available) / total) * 100 : 0;
  });
}

