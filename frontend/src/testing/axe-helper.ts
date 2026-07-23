/**
 * Accessibility testing helper using axe-core.
 * 
 * Provides a simple wrapper around axe.run() for use in component tests.
 * 
 * Example usage:
 * ```typescript
 * import { runAxe } from '../../testing/axe-helper';
 * 
 * it('should have no accessibility violations', async () => {
 *   const results = await runAxe(fixture.nativeElement);
 *   expect(results.violations).toHaveLength(0);
 * });
 * ```
 * 
 * Official docs: https://github.com/dequelabs/axe-core
 */

import * as axe from 'axe-core';

/**
 * Run axe accessibility checks on a DOM element.
 * 
 * @param element - The DOM element to check (typically fixture.nativeElement)
 * @param options - Optional axe configuration
 * @returns Promise resolving to axe results
 */
export async function runAxe(
  element: Element,
  options?: axe.RunOptions
): Promise<axe.AxeResults> {
  if (options) {
    return axe.run(element, options);
  }
  return axe.run(element);
}

/**
 * Assert that there are no accessibility violations.
 * Throws an error with violation details if any are found.
 * 
 * @param element - The DOM element to check
 * @param options - Optional axe configuration
 */
export async function assertNoAxeViolations(
  element: Element,
  options?: axe.RunOptions
): Promise<void> {
  const results = await runAxe(element, options);
  
  if (results.violations.length > 0) {
    const violationMessages = results.violations.map(violation => {
      const nodes = violation.nodes.map(node => node.html).join('\n  ');
      return `${violation.id}: ${violation.description}\n  ${nodes}`;
    }).join('\n\n');
    
    throw new Error(
      `Found ${results.violations.length} accessibility violation(s):\n\n${violationMessages}`
    );
  }
}
