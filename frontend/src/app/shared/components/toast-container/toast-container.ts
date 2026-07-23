import { Component, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ToastService, Toast } from '../../../core/services/toast.service';

@Component({
  selector: 'app-toast-container',
  standalone: true,
  imports: [],
  templateUrl: './toast-container.html',
  styleUrl: './toast-container.scss',
})
export class ToastContainerComponent {
  private toastService = inject(ToastService);

  readonly toasts = toSignal(this.toastService.toasts$, { initialValue: [] as Toast[] });

  dismiss(id: number): void {
    this.toastService.remove(id);
  }

  onMouseEnter(id: number): void {
    this.toastService.pause(id);
  }

  onMouseLeave(id: number): void {
    this.toastService.resume(id);
  }

  onFocus(id: number): void {
    this.toastService.pause(id);
  }

  onBlur(id: number): void {
    this.toastService.resume(id);
  }

  iconFor(type: Toast['type']): string {
    switch (type) {
      case 'success': return 'bi-check-circle-fill';
      case 'error':   return 'bi-x-circle-fill';
      case 'warning': return 'bi-exclamation-triangle-fill';
      case 'info':    return 'bi-info-circle-fill';
    }
  }
}
