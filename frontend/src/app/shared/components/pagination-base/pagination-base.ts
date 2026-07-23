export interface PaginationState {
  currentPage: number;
  pageSize: number;
  totalElements: number;
  totalPages: number;
}

export const DEFAULT_PAGE_SIZE = 10;
export const MAX_VISIBLE_PAGES = 5;

export class PaginationHelper {
  private static validatePageSize(pageSize: number): void {
    if (!Number.isInteger(pageSize) || pageSize <= 0) {
      throw new Error(`pageSize must be a positive integer, got: ${pageSize}`);
    }
  }

  private static validateTotalElements(totalElements: number): void {
    if (!Number.isInteger(totalElements) || totalElements < 0) {
      throw new Error(
        `totalElements must be a non-negative integer, got: ${totalElements}`
      );
    }
  }

  static createPaginationState(pageSize = DEFAULT_PAGE_SIZE): PaginationState {
    PaginationHelper.validatePageSize(pageSize);
    return {
      currentPage: 0,
      pageSize,
      totalElements: 0,
      totalPages: 0,
    };
  }

  static updatePaginationState(
    state: PaginationState,
    totalElements: number,
    pageSize: number
  ): PaginationState {
    PaginationHelper.validatePageSize(pageSize);
    PaginationHelper.validateTotalElements(totalElements);

    // No elements means no pages. Coercing to 1 (the old `|| 1`) reported a
    // phantom page for empty results.
    const totalPages =
      totalElements === 0 ? 0 : Math.ceil(totalElements / pageSize);
    // Clamp currentPage after totals shrink so it never points past the last page.
    const currentPage =
      totalPages === 0 ? 0 : Math.min(state.currentPage, totalPages - 1);

    return {
      currentPage,
      pageSize,
      totalElements,
      totalPages,
    };
  }

  static getPageNumbers(currentPage: number, totalPages: number): number[] {
    const maxPages = MAX_VISIBLE_PAGES;
    if (totalPages <= maxPages) {
      return Array.from({ length: totalPages }, (_, i) => i);
    }

    const halfWindow = Math.floor(maxPages / 2);
    let start = Math.max(0, currentPage - halfWindow);
    const end = Math.min(totalPages - 1, start + maxPages - 1);

    if (end - start + 1 < maxPages) {
      start = Math.max(0, end - maxPages + 1);
    }

    return Array.from({ length: end - start + 1 }, (_, i) => start + i);
  }

  static isFirstPage(currentPage: number): boolean {
    return currentPage === 0;
  }

  static isLastPage(currentPage: number, totalPages: number): boolean {
    // With no pages there is no "last page"; guard against the -1 wraparound
    // that made isLastPage(0, 0) return true.
    return totalPages > 0 && currentPage >= totalPages - 1;
  }
}
