// Pure helpers for paginated tables: which rows are on a page, and which page buttons to show.

export interface PageInfo {
  /** The page shown, 1-based, kept inside 1..pageCount. */
  page: number;
  pageCount: number;
  /** Slice bounds into the full list: items.slice(start, end). */
  start: number;
  end: number;
  /** For "Showing 11–20 of 74": the first and last row on the page (both 0 when there are no rows). */
  from: number;
  to: number;
  total: number;
}

export const PAGE_SIZES = [10, 25, 50];

export function paginate(total: number, page: number, size: number): PageInfo {
  const pageSize = Math.max(1, Math.floor(size));
  const pageCount = Math.max(1, Math.ceil(total / pageSize));
  const current = Math.min(Math.max(1, Math.floor(page) || 1), pageCount);
  const start = (current - 1) * pageSize;
  const end = Math.min(total, start + pageSize);
  return { page: current, pageCount, start, end, from: total === 0 ? 0 : start + 1, to: end, total };
}

/**
 * The page buttons to show: the first, the last, and the pages around the current one, with a gap marker where pages
 * are left out ([1, 'gap', 4, 5, 6, 'gap', 10]). A gap that would hide a single page shows that page instead.
 */
export function pageWindow(page: number, pageCount: number): (number | 'gap')[] {
  const wanted = new Set([1, pageCount, page - 1, page, page + 1].filter((n) => n >= 1 && n <= pageCount));
  const pages = [...wanted].sort((a, b) => a - b);
  const result: (number | 'gap')[] = [];
  pages.forEach((n, index) => {
    const before = pages[index - 1];
    if (before !== undefined && n - before === 2) result.push(before + 1);
    else if (before !== undefined && n - before > 2) result.push('gap');
    result.push(n);
  });
  return result;
}
