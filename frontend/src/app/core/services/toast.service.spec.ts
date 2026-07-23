import { TestBed } from '@angular/core/testing';
import { firstValueFrom, skip } from 'rxjs';
import { ToastService } from './toast.service';

/**
 * ToastService unit tests.
 *
 * Covers toast notification management:
 * - Creating toasts with different types
 * - Auto-dismiss timing
 * - Manual dismiss
 * - Toast queue management
 * - Max toast limit enforcement
 */
describe('ToastService', () => {
  let service: ToastService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [ToastService],
    });
    service = TestBed.inject(ToastService);
  });

  afterEach(() => {
    service.clear();
  });

  describe('success', () => {
    it('creates a success toast', async () => {
      service.success('Operation successful');

      const toasts = await firstValueFrom(service.toasts$);
      expect(toasts).toHaveLength(1);
      expect(toasts[0].type).toBe('success');
      expect(toasts[0].message).toBe('Operation successful');
      expect(toasts[0].duration).toBe(3000);
    });

    it('creates success toast with custom duration', async () => {
      service.success('Custom duration', 5000);

      const toasts = await firstValueFrom(service.toasts$);
      expect(toasts[0].duration).toBe(5000);
    });
  });

  describe('error', () => {
    it('creates an error toast', async () => {
      service.error('Something went wrong');

      const toasts = await firstValueFrom(service.toasts$);
      expect(toasts).toHaveLength(1);
      expect(toasts[0].type).toBe('error');
      expect(toasts[0].message).toBe('Something went wrong');
      expect(toasts[0].duration).toBe(0);
    });

    it('error toasts are persistent (duration 0)', async () => {
      service.error('Error message');

      const toasts = await firstValueFrom(service.toasts$);
      expect(toasts[0].duration).toBe(0);
    });
  });

  describe('warning', () => {
    it('creates a warning toast', async () => {
      service.warning('Please be careful');

      const toasts = await firstValueFrom(service.toasts$);
      expect(toasts).toHaveLength(1);
      expect(toasts[0].type).toBe('warning');
      expect(toasts[0].message).toBe('Please be careful');
      expect(toasts[0].duration).toBe(7000);
    });
  });

  describe('info', () => {
    it('creates an info toast', async () => {
      service.info('FYI: something happened');

      const toasts = await firstValueFrom(service.toasts$);
      expect(toasts).toHaveLength(1);
      expect(toasts[0].type).toBe('info');
      expect(toasts[0].message).toBe('FYI: something happened');
      expect(toasts[0].duration).toBe(3000);
    });
  });

  // The app is zoneless (zone.js is not a dependency), so Angular's
  // `fakeAsync`/`tick` helpers are unavailable. ToastService schedules its
  // auto-dismiss with plain `setTimeout`, which Vitest's fake timers control.
  describe('auto-dismiss', () => {
    beforeEach(() => {
      vi.useFakeTimers();
    });

    afterEach(() => {
      vi.useRealTimers();
    });

    it('automatically removes success toast after duration', async () => {
      service.success('Auto-dismiss test');
      let toasts = await firstValueFrom(service.toasts$);
      expect(toasts).toHaveLength(1);

      vi.advanceTimersByTime(3000);

      toasts = await firstValueFrom(service.toasts$);
      expect(toasts).toHaveLength(0);
    });

    it('automatically removes info toast after duration', async () => {
      service.info('Info message');
      let toasts = await firstValueFrom(service.toasts$);
      expect(toasts).toHaveLength(1);

      vi.advanceTimersByTime(3000);

      toasts = await firstValueFrom(service.toasts$);
      expect(toasts).toHaveLength(0);
    });

    it('automatically removes warning toast after 7s', async () => {
      service.warning('Warning message');
      let toasts = await firstValueFrom(service.toasts$);
      expect(toasts).toHaveLength(1);

      vi.advanceTimersByTime(7000);

      toasts = await firstValueFrom(service.toasts$);
      expect(toasts).toHaveLength(0);
    });

    it('does not auto-dismiss error toasts', async () => {
      service.error('Error message');
      let toasts = await firstValueFrom(service.toasts$);
      expect(toasts).toHaveLength(1);

      vi.advanceTimersByTime(10000);

      toasts = await firstValueFrom(service.toasts$);
      expect(toasts).toHaveLength(1);
    });

    it('respects custom duration', async () => {
      service.success('Custom duration', 1000);
      let toasts = await firstValueFrom(service.toasts$);
      expect(toasts).toHaveLength(1);

      vi.advanceTimersByTime(1000);

      toasts = await firstValueFrom(service.toasts$);
      expect(toasts).toHaveLength(0);
    });
  });

  describe('remove', () => {
    it('manually removes a toast by id', async () => {
      const id = service.success('Remove me');
      let toasts = await firstValueFrom(service.toasts$);
      expect(toasts).toHaveLength(1);

      service.remove(id);

      toasts = await firstValueFrom(service.toasts$);
      expect(toasts).toHaveLength(0);
    });

    it('removes specific toast from multiple toasts', async () => {
      const id1 = service.success('Toast 1');
      const id2 = service.success('Toast 2');
      const id3 = service.success('Toast 3');
      let toasts = await firstValueFrom(service.toasts$);
      expect(toasts).toHaveLength(3);

      service.remove(id2);

      toasts = await firstValueFrom(service.toasts$);
      expect(toasts).toHaveLength(2);
      expect(toasts.find(t => t.id === id1)).toBeDefined();
      expect(toasts.find(t => t.id === id2)).toBeUndefined();
      expect(toasts.find(t => t.id === id3)).toBeDefined();
    });

    it('handles removing non-existent toast gracefully', async () => {
      service.success('Toast');
      let toasts = await firstValueFrom(service.toasts$);
      expect(toasts).toHaveLength(1);

      service.remove(999);

      toasts = await firstValueFrom(service.toasts$);
      expect(toasts).toHaveLength(1);
    });
  });

  describe('clear', () => {
    beforeEach(() => {
      vi.useFakeTimers();
    });

    afterEach(() => {
      vi.useRealTimers();
    });

    it('removes all toasts', async () => {
      service.success('Toast 1');
      service.error('Toast 2');
      service.warning('Toast 3');
      let toasts = await firstValueFrom(service.toasts$);
      expect(toasts).toHaveLength(3);

      service.clear();

      toasts = await firstValueFrom(service.toasts$);
      expect(toasts).toHaveLength(0);
    });

    it('clears all timers when clearing toasts', async () => {
      service.success('Toast 1');
      service.success('Toast 2');

      service.clear();
      let toasts = await firstValueFrom(service.toasts$);
      expect(toasts).toHaveLength(0);

      vi.advanceTimersByTime(5000);

      toasts = await firstValueFrom(service.toasts$);
      expect(toasts).toHaveLength(0);
    });
  });

  describe('toast queue', () => {
    it('allows multiple toasts up to max limit', async () => {
      service.success('Toast 1');
      service.success('Toast 2');
      service.success('Toast 3');
      service.success('Toast 4');
      service.success('Toast 5');

      const toasts = await firstValueFrom(service.toasts$);
      expect(toasts).toHaveLength(5);
    });

    it('removes oldest toast when exceeding max limit', async () => {
      const id1 = service.success('Toast 1');
      service.success('Toast 2');
      service.success('Toast 3');
      service.success('Toast 4');
      service.success('Toast 5');
      service.success('Toast 6');

      const toasts = await firstValueFrom(service.toasts$);
      expect(toasts).toHaveLength(5);
      expect(toasts.find(t => t.id === id1)).toBeUndefined();
      expect(toasts[0].message).toBe('Toast 2');
      expect(toasts[4].message).toBe('Toast 6');
    });

    it('enforces max limit with mixed toast types', async () => {
      service.success('Toast 1');
      service.error('Toast 2');
      service.warning('Toast 3');
      service.info('Toast 4');
      service.success('Toast 5');
      service.error('Toast 6');

      const toasts = await firstValueFrom(service.toasts$);
      expect(toasts).toHaveLength(5);
      expect(toasts[0].message).toBe('Toast 2');
      expect(toasts[4].message).toBe('Toast 6');
    });
  });

  describe('pause and resume', () => {
    beforeEach(() => {
      vi.useFakeTimers();
    });

    afterEach(() => {
      vi.useRealTimers();
    });

    it('pauses auto-dismiss when toast is paused', async () => {
      const id = service.success('Pause me');
      let toasts = await firstValueFrom(service.toasts$);
      expect(toasts).toHaveLength(1);

      service.pause(id);
      vi.advanceTimersByTime(5000);

      toasts = await firstValueFrom(service.toasts$);
      expect(toasts).toHaveLength(1);
    });

    it('resumes auto-dismiss when toast is resumed', async () => {
      const id = service.success('Resume me');

      service.pause(id);
      vi.advanceTimersByTime(2000);

      service.resume(id);
      vi.advanceTimersByTime(3000);

      const toasts = await firstValueFrom(service.toasts$);
      expect(toasts).toHaveLength(0);
    });

    it('does not resume error toasts (duration 0)', async () => {
      const id = service.error('Error toast');

      service.pause(id);
      service.resume(id);

      vi.advanceTimersByTime(10000);

      const toasts = await firstValueFrom(service.toasts$);
      expect(toasts).toHaveLength(1);
    });

    it('handles pausing non-existent toast gracefully', () => {
      expect(() => service.pause(999)).not.toThrow();
    });

    it('handles resuming non-existent toast gracefully', () => {
      expect(() => service.resume(999)).not.toThrow();
    });
  });

  describe('toasts$ observable', () => {
    it('emits current toasts immediately on subscribe', async () => {
      service.success('Toast 1');
      service.success('Toast 2');

      const toasts = await firstValueFrom(service.toasts$);
      expect(toasts).toHaveLength(2);
    });

    it('emits updates when new toast is added', async () => {
      // toasts$ is a BehaviorSubject, so it replays the current (empty) value
      // immediately — skip it to observe the emission caused by success().
      const promise = firstValueFrom(service.toasts$.pipe(skip(1)));
      service.success('New toast');

      const toasts = await promise;
      expect(toasts).toHaveLength(1);
      expect(toasts[0].message).toBe('New toast');
    });

    it('emits updates when toast is removed', async () => {
      const id = service.success('Remove me');
      
      service.remove(id);
      
      const toasts = await firstValueFrom(service.toasts$);
      expect(toasts).toHaveLength(0);
    });
  });

  describe('toast IDs', () => {
    it('assigns unique IDs to each toast', () => {
      const id1 = service.success('Toast 1');
      const id2 = service.success('Toast 2');
      const id3 = service.success('Toast 3');

      expect(id1).not.toBe(id2);
      expect(id2).not.toBe(id3);
      expect(id1).not.toBe(id3);
    });

    it('IDs increment sequentially', () => {
      service.clear();
      const id1 = service.success('Toast 1');
      const id2 = service.success('Toast 2');

      expect(id2).toBe(id1 + 1);
    });
  });
});
