import { describe, expect, it } from 'vitest';
import { pageWindow, paginate } from './paging';

describe('paginate', () => {
  it('cuts a list into pages', () => {
    expect(paginate(74, 1, 25)).toEqual({ page: 1, pageCount: 3, start: 0, end: 25, from: 1, to: 25, total: 74 });
    expect(paginate(74, 3, 25)).toEqual({ page: 3, pageCount: 3, start: 50, end: 74, from: 51, to: 74, total: 74 });
  });

  it('keeps the page inside the list, for example after a filter shortens it', () => {
    expect(paginate(12, 9, 10).page).toBe(2);
    expect(paginate(12, 0, 10).page).toBe(1);
    expect(paginate(12, Number.NaN, 10).page).toBe(1);
  });

  it('copes with an empty list', () => {
    expect(paginate(0, 1, 10)).toEqual({ page: 1, pageCount: 1, start: 0, end: 0, from: 0, to: 0, total: 0 });
  });
});

describe('pageWindow', () => {
  it('shows every page when there are few', () => {
    expect(pageWindow(1, 1)).toEqual([1]);
    expect(pageWindow(2, 5)).toEqual([1, 2, 3, 4, 5]);
  });

  it('leaves a gap only where pages are hidden', () => {
    expect(pageWindow(1, 10)).toEqual([1, 2, 'gap', 10]);
    expect(pageWindow(5, 10)).toEqual([1, 'gap', 4, 5, 6, 'gap', 10]);
    expect(pageWindow(10, 10)).toEqual([1, 'gap', 9, 10]);
  });

  it('shows the page instead of a gap that would hide just one', () => {
    expect(pageWindow(4, 10)).toEqual([1, 2, 3, 4, 5, 'gap', 10]);
    expect(pageWindow(7, 10)).toEqual([1, 'gap', 6, 7, 8, 9, 10]);
  });
});
