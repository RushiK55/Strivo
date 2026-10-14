'use client';
// What every client has been doing, newest first.
import { useCallback, useState } from 'react';
import { EventList } from '@/components/EventList';
import { TablePagination } from '@/components/TablePagination';
import { ErrorBox, FilterToggle, MoreButton, Page, PageSkeleton, SearchBox, type FilterOption } from '@/components/ui';
import { Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from '@/components/ui/card';
import { listEvents, type Cursor } from '@/lib/data';
import { usePagination, usePaged } from '@/lib/hooks';
import { eventKinds, eventLabel, safeSummary } from '@/lib/logic';
import { loadNames, nameOf } from '@/lib/names';

export function ActivityView() {
  const fetchPage = useCallback(async (after: Cursor | null) => {
    const page = await listEvents({ after });
    await loadNames(page.items.map((event) => event.uid));
    return page;
  }, []);
  const events = usePaged(fetchPage);
  const [kind, setKind] = useState('all');
  const [search, setSearch] = useState('');

  const text = search.trim().toLowerCase();
  const rows = events.items.filter((event) =>
    (kind === 'all' || eventLabel(event.type) === kind)
    && (!text || safeSummary(event.type, event.summary).toLowerCase().includes(text) || nameOf(event.uid).toLowerCase().includes(text)));
  const paging = usePagination(rows.length, 25);

  const options: FilterOption<string>[] = [{ value: 'all', label: 'Everything' }, ...eventKinds(events.items).map((label) => ({ value: label, label }))];

  return (
    <Page title="Activity" description="Everything your clients add or change. What a client eats is never shown, only that they logged a meal.">
      {events.error ? <ErrorBox error={events.error} /> : null}
      {events.loading ? <PageSkeleton /> : (
        <Card>
          <CardHeader className="gap-3 sm:flex sm:items-center sm:justify-between">
            <div>
              <CardTitle className="text-lg">Activity feed</CardTitle>
              <CardDescription>{`${events.items.length} loaded${events.hasMore ? ', older activity available' : ''}`}</CardDescription>
            </div>
            <SearchBox value={search} onChange={(next) => { setSearch(next); paging.setPage(1); }} placeholder="Search by user or text" label="Search activity" />
          </CardHeader>
          <CardContent className="space-y-4">
            <FilterToggle label="Filter by kind" value={kind} options={options} onChange={(next) => { setKind(next); paging.setPage(1); }} />
            <EventList events={rows.slice(paging.start, paging.end)} showUser />
            <MoreButton hasMore={events.hasMore} busy={events.loadingMore} error={events.moreError} onClick={events.loadMore}>Load older activity</MoreButton>
          </CardContent>
          <CardFooter className="border-t">
            <div className="w-full">
              <TablePagination info={paging} size={paging.size} onPage={paging.setPage} onSize={paging.setSize} noun="actions" />
            </div>
          </CardFooter>
        </Card>
      )}
    </Page>
  );
}
