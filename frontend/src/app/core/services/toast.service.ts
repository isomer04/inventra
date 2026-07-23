import { Injectable } from '@angular/core';
import { BehaviorSubject } from 'rxjs';

export interface Toast {
  id: number;
  message: string;
  type: 'success' | 'error' | 'info' | 'warning';
  /** 0 = persistent (error toasts). Milliseconds otherwise. */
  duration: number;
}

const MAX_TOASTS = 5;

@Injectable({ providedIn: 'root' })
export class ToastService {
  private readonly toastsSubject = new BehaviorSubject<Toast[]>([]);
  readonly toasts$ = this.toastsSubject.asObservable();

  /** Timer handles are kept private and never exposed via the observable */
  private readonly timers = new Map<number, ReturnType<typeof setTimeout>>();

  private nextId = 1;

  /** Show a toast. Error toasts never auto-dismiss (duration forced to 0). */
  show(message: string, type: Toast['type'] = 'info', duration?: number): number {
    // Intentional defaults:
    //   success / info  → 3s (quick confirmation, low urgency)
    //   warning         → 7s (user may need time to read and act)
    //   error           → persistent until manually dismissed
    const defaultDuration: Record<Toast['type'], number> = {
      success: 3000,
      info:    3000,
      warning: 7000,
      error:   0,
    };
    const resolvedDuration = type === 'error' ? 0 : (duration ?? defaultDuration[type]);

    const id = this.nextId++;
    const toast: Toast = { id, message, type, duration: resolvedDuration };

    let current = [...this.toastsSubject.value];

    // Enforce max-5 cap: drop oldest before adding
    if (current.length >= MAX_TOASTS) {
      const oldest = current[0];
      this.clearTimer(oldest.id);
      current = current.slice(1);
    }

    if (resolvedDuration > 0) {
      this.timers.set(id, setTimeout(() => this.remove(id), resolvedDuration));
    }

    this.toastsSubject.next([...current, toast]);
    return id;
  }

  success(message: string, duration?: number): number { return this.show(message, 'success', duration); }
  error(message: string): number                      { return this.show(message, 'error'); }
  info(message: string, duration?: number): number    { return this.show(message, 'info', duration); }
  warning(message: string, duration?: number): number { return this.show(message, 'warning', duration); }

  /** Pause auto-dismiss for a specific toast (e.g., on hover/focus). */
  pause(id: number): void {
    this.clearTimer(id);
  }

  /** Resume auto-dismiss after a pause. */
  resume(id: number): void {
    const toast = this.toastsSubject.value.find(t => t.id === id);
    if (!toast || toast.duration <= 0 || this.timers.has(id)) return;
    this.timers.set(id, setTimeout(() => this.remove(id), toast.duration));
  }

  remove(id: number): void {
    this.clearTimer(id);
    this.toastsSubject.next(this.toastsSubject.value.filter(t => t.id !== id));
  }

  clear(): void {
    this.toastsSubject.value.forEach(t => this.clearTimer(t.id));
    this.toastsSubject.next([]);
  }

  private clearTimer(id: number): void {
    const handle = this.timers.get(id);
    if (handle !== undefined) {
      clearTimeout(handle);
      this.timers.delete(id);
    }
  }
}
