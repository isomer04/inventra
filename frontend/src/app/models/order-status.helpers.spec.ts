import { OrderStatus } from './order.model';
import { ORDER_STATUS_CONFIG, statusConfig } from './order-status.helpers';

/**
 * Order Status Helpers unit tests.
 *
 * Validates the order status configuration, label mappings,
 * badge classes, and color assignments for use in UI components.
 */
describe('Order Status Helpers', () => {

  describe('ORDER_STATUS_CONFIG', () => {
    it('has configuration for all order statuses', () => {
      const allStatuses = Object.values(OrderStatus);
      
      allStatuses.forEach(status => {
        expect(ORDER_STATUS_CONFIG[status]).toBeDefined();
        expect(ORDER_STATUS_CONFIG[status].label).toBeTruthy();
        expect(ORDER_STATUS_CONFIG[status].badgeClass).toBeTruthy();
        expect(ORDER_STATUS_CONFIG[status].color).toBeTruthy();
      });
    });

    it('DRAFT status has correct configuration', () => {
      const config = ORDER_STATUS_CONFIG[OrderStatus.DRAFT];
      
      expect(config.label).toBe('Draft');
      expect(config.badgeClass).toBe('bg-secondary');
      expect(config.color).toBe('#adb5bd');
    });

    it('SUBMITTED status has correct configuration', () => {
      const config = ORDER_STATUS_CONFIG[OrderStatus.SUBMITTED];
      
      expect(config.label).toBe('Submitted');
      expect(config.badgeClass).toBe('bg-primary');
      expect(config.color).toBe('#0d6efd');
    });

    it('APPROVED status has correct configuration', () => {
      const config = ORDER_STATUS_CONFIG[OrderStatus.APPROVED];
      
      expect(config.label).toBe('Approved');
      expect(config.badgeClass).toBe('bg-info text-dark');
      expect(config.color).toBe('#6610f2');
    });

    it('PICKING status has correct configuration', () => {
      const config = ORDER_STATUS_CONFIG[OrderStatus.PICKING];
      
      expect(config.label).toBe('Picking');
      expect(config.badgeClass).toBe('bg-warning text-dark');
      expect(config.color).toBe('#fd7e14');
    });

    it('SHIPPED status has correct configuration', () => {
      const config = ORDER_STATUS_CONFIG[OrderStatus.SHIPPED];
      
      expect(config.label).toBe('Shipped');
      expect(config.badgeClass).toBe('bg-info text-dark');
      expect(config.color).toBe('#0dcaf0');
    });

    it('DELIVERED status has correct configuration', () => {
      const config = ORDER_STATUS_CONFIG[OrderStatus.DELIVERED];
      
      expect(config.label).toBe('Delivered');
      expect(config.badgeClass).toBe('bg-success');
      expect(config.color).toBe('#198754');
    });

    it('REJECTED status has correct configuration', () => {
      const config = ORDER_STATUS_CONFIG[OrderStatus.REJECTED];
      
      expect(config.label).toBe('Rejected');
      expect(config.badgeClass).toBe('bg-danger');
      expect(config.color).toBe('#dc3545');
    });

    it('CANCELLED status has correct configuration', () => {
      const config = ORDER_STATUS_CONFIG[OrderStatus.CANCELLED];
      
      expect(config.label).toBe('Cancelled');
      expect(config.badgeClass).toBe('bg-secondary');
      expect(config.color).toBe('#6c757d');
    });
  });

  describe('statusConfig', () => {
    it('returns correct config for valid status', () => {
      const config = statusConfig(OrderStatus.APPROVED);
      
      expect(config).toEqual(ORDER_STATUS_CONFIG[OrderStatus.APPROVED]);
      expect(config.label).toBe('Approved');
    });

    it('returns all configured statuses', () => {
      const allStatuses = Object.values(OrderStatus);
      
      allStatuses.forEach(status => {
        const config = statusConfig(status);
        expect(config).toEqual(ORDER_STATUS_CONFIG[status]);
      });
    });

    it('returns default config for invalid status', () => {
      const config = statusConfig('INVALID_STATUS');
      
      expect(config.label).toBe('INVALID_STATUS');
      expect(config.badgeClass).toBe('bg-secondary');
      expect(config.color).toBe('#6c757d');
    });

    it('returns default config for null status', () => {
      const config = statusConfig(null as any);
      
      expect(config.label).toBeNull();
      expect(config.badgeClass).toBe('bg-secondary');
      expect(config.color).toBe('#6c757d');
    });

    it('returns default config for undefined status', () => {
      const config = statusConfig(undefined as any);
      
      expect(config.label).toBeUndefined();
      expect(config.badgeClass).toBe('bg-secondary');
      expect(config.color).toBe('#6c757d');
    });

    it('returns default config for empty string status', () => {
      const config = statusConfig('');
      
      expect(config.label).toBe('');
      expect(config.badgeClass).toBe('bg-secondary');
      expect(config.color).toBe('#6c757d');
    });
  });

  describe('badge classes', () => {
    it('success status uses green badge', () => {
      const config = ORDER_STATUS_CONFIG[OrderStatus.DELIVERED];
      expect(config.badgeClass).toContain('success');
    });

    it('error status uses red badge', () => {
      const config = ORDER_STATUS_CONFIG[OrderStatus.REJECTED];
      expect(config.badgeClass).toContain('danger');
    });

    it('warning status uses orange badge', () => {
      const config = ORDER_STATUS_CONFIG[OrderStatus.PICKING];
      expect(config.badgeClass).toContain('warning');
    });

    it('info status uses blue badge', () => {
      const config = ORDER_STATUS_CONFIG[OrderStatus.SUBMITTED];
      expect(config.badgeClass).toContain('primary');
    });

    it('neutral statuses use secondary badge', () => {
      expect(ORDER_STATUS_CONFIG[OrderStatus.DRAFT].badgeClass).toContain('secondary');
      expect(ORDER_STATUS_CONFIG[OrderStatus.CANCELLED].badgeClass).toContain('secondary');
    });
  });

  describe('color consistency', () => {
    it('all colors are valid hex codes', () => {
      const allStatuses = Object.values(OrderStatus);
      const hexColorRegex = /^#[0-9a-f]{6}$/i;
      
      allStatuses.forEach(status => {
        const config = ORDER_STATUS_CONFIG[status];
        expect(config.color).toMatch(hexColorRegex);
      });
    });

    it('success status has green color', () => {
      const config = ORDER_STATUS_CONFIG[OrderStatus.DELIVERED];
      expect(config.color).toBe('#198754'); // Bootstrap green
    });

    it('danger status has red color', () => {
      const config = ORDER_STATUS_CONFIG[OrderStatus.REJECTED];
      expect(config.color).toBe('#dc3545'); // Bootstrap red
    });

    it('warning status has orange color', () => {
      const config = ORDER_STATUS_CONFIG[OrderStatus.PICKING];
      expect(config.color).toBe('#fd7e14'); // Bootstrap orange
    });

    it('primary status has blue color', () => {
      const config = ORDER_STATUS_CONFIG[OrderStatus.SUBMITTED];
      expect(config.color).toBe('#0d6efd'); // Bootstrap blue
    });
  });

  describe('label consistency', () => {
    it('labels are human-readable and capitalized', () => {
      const allStatuses = Object.values(OrderStatus);
      
      allStatuses.forEach(status => {
        const config = ORDER_STATUS_CONFIG[status];
        expect(config.label).toBeTruthy();
        expect(config.label[0]).toBe(config.label[0].toUpperCase());
      });
    });

    it('labels match status semantic meaning', () => {
      expect(ORDER_STATUS_CONFIG[OrderStatus.DRAFT].label).toBe('Draft');
      expect(ORDER_STATUS_CONFIG[OrderStatus.SUBMITTED].label).toBe('Submitted');
      expect(ORDER_STATUS_CONFIG[OrderStatus.APPROVED].label).toBe('Approved');
      expect(ORDER_STATUS_CONFIG[OrderStatus.PICKING].label).toBe('Picking');
      expect(ORDER_STATUS_CONFIG[OrderStatus.SHIPPED].label).toBe('Shipped');
      expect(ORDER_STATUS_CONFIG[OrderStatus.DELIVERED].label).toBe('Delivered');
      expect(ORDER_STATUS_CONFIG[OrderStatus.REJECTED].label).toBe('Rejected');
      expect(ORDER_STATUS_CONFIG[OrderStatus.CANCELLED].label).toBe('Cancelled');
    });
  });
});
