'use client';
// Loading data in a component. `load` must be stable (wrap it in useCallback): a new function means a new request.
import { useCallback, useEffect, useState } from 'react';
import type { Cursor, Page } from './data';
import { paginate } from './paging';

interface LoadState<T> { load: unknown; tick: number; data?: T; error?: unknown }

export interface Loaded<T> {
  /** The latest result of this `load`; kept while it reloads, undefined when `load` itself changed. */
  data: T | undefined;
  error: unknown;
  loading: boolean;
  reload: () => void;
}

export function useLoad<T>(load: () => Promise<T>): Loaded<T> {
  const [tick, setTick] = useState(0);
  const [state, setState] = useState<LoadState<T> | null>(null);

  useEffect(() => {
    let cancelled = false;
    load().then(
      (data) => { if (!cancelled) setState({ load, tick, data }); },
      (error: unknown) => { if (!cancelled) setState({ load, tick, error }); },
    );
    return () => { cancelled = true; };
  }, [load, tick]);

  const same = state?.load === load;
  const fresh = same && state.tick === tick;
  return {
    data: same ? state.data : undefined,
    error: fresh ? state.error : undefined,
    loading: !fresh,
    reload: useCallback(() => setTick((value) => value + 1), []),
  };
}

export interface Paged<T> {
  items: T[];
  loading: boolean;
  error: unknown;
  hasMore: boolean;
  loadingMore: boolean;
  moreError: unknown;
  loadMore: () => void;
}

interface More<T> { source: unknown; items: T[]; last: Cursor | null; hasMore: boolean }

/** A list that loads a first page and then more on request. `fetchPage` must be stable (useCallback). */
export function usePaged<T>(fetchPage: (after: Cursor | null) => Promise<Page<T>>): Paged<T> {
  const first = useLoad(useCallback(() => fetchPage(null), [fetchPage]));
  const [more, setMore] = useState<More<T> | null>(null);
  const [loadingMore, setLoadingMore] = useState(false);
  const [moreError, setMoreError] = useState<unknown>(undefined);

  // Pages loaded after the first only count while the first page is the one they continued from.
  const extra = more && more.source === first.data ? more : null;
  const last = extra ? extra.last : first.data?.last ?? null;
  const hasMore = extra ? extra.hasMore : first.data?.hasMore ?? false;
  const items = [...(first.data?.items ?? []), ...(extra?.items ?? [])];

  const loadMore = useCallback(() => {
    const source = first.data;
    if (!source) return;
    setLoadingMore(true);
    setMoreError(undefined);
    fetchPage(last).then(
      (page) => {
        setMore({ source, items: [...(extra?.items ?? []), ...page.items], last: page.last, hasMore: page.hasMore });
        setLoadingMore(false);
      },
      (error: unknown) => { setMoreError(error); setLoadingMore(false); },
    );
  }, [fetchPage, first.data, last, extra]);

  return { items, loading: first.loading && !first.data, error: first.error, hasMore, loadingMore, moreError, loadMore };
}

/** Which page of a list is showing, and how many rows per page. Pass the length of the (filtered) list. */
export function usePagination(total: number, initialSize = 10) {
  const [page, setPage] = useState(1);
  const [size, setSizeState] = useState(initialSize);
  const info = paginate(total, page, size);
  const setSize = useCallback((next: number) => { setSizeState(next); setPage(1); }, []);
  return { ...info, size, setPage, setSize };
}
