'use client';
// All the clients: who is training, how their body weight and eating are going. The most recently active first.
import { useCallback, useEffect, useState } from 'react';
import { IntakeCell, TrainingBadge, WeekCell, WeightCell } from '@/components/ClientParts';
import { UserCell } from '@/components/EventList';
import { TablePagination } from '@/components/TablePagination';
import { Empty, ErrorBox, FilterToggle, MoreButton, Page, PageSkeleton, SearchBox, type FilterOption } from '@/components/ui';
import { Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from '@/components/ui/card';
import { NativeSelect, NativeSelectOption } from '@/components/ui/native-select';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { lastWorkoutText, sortClients, TRAINING_LABEL, trainingStatus, type ClientSort, type TrainingStatus } from '@/lib/coaching';
import { listUsers, type Cursor } from '@/lib/data';
import { usePagination, usePaged } from '@/lib/hooks';
import { matchesUser } from '@/lib/logic';
import { rememberUsers } from '@/lib/names';

type Filter = 'all' | TrainingStatus;

const STATUSES: TrainingStatus[] = ['ontrack', 'slipping', 'missing', 'never', 'unknown'];

export function ClientsView() {
  const fetchPage = useCallback((after: Cursor | null) => listUsers(after), []);
  const clients = usePaged(fetchPage);
  const [search, setSearch] = useState('');
  const [filter, setFilter] = useState<Filter>('all');
  const [sort, setSort] = useState<ClientSort>('recent');

  useEffect(() => { rememberUsers(clients.items); }, [clients.items]);

  const counts = Object.fromEntries(STATUSES.map((status) => [status, 0])) as Record<TrainingStatus, number>;
  for (const client of clients.items) counts[trainingStatus(client.stats)] += 1;
  const rows = sortClients(clients.items, sort).filter((client) => (filter === 'all' || trainingStatus(client.stats) === filter) && matchesUser(client, search));
  const paging = usePagination(rows.length, 10);

  const options: FilterOption<Filter>[] = [
    { value: 'all', label: 'All', count: clients.items.length },
    ...STATUSES.filter((status) => counts[status] > 0 || status === filter).map((status) => ({ value: status, label: TRAINING_LABEL[status], count: counts[status] })),
  ];

  return (
    <Page title="Clients" description="Everyone you coach: who is training, and how their body weight and eating are going.">
      {clients.error ? <ErrorBox error={clients.error} /> : null}
      {clients.loading ? <PageSkeleton /> : (
        <Card>
          <CardHeader className="gap-3 sm:flex sm:items-center sm:justify-between">
            <div>
              <CardTitle className="text-lg">All clients</CardTitle>
              <CardDescription>{`${clients.items.length} loaded${clients.hasMore ? ', more available' : ''}`}</CardDescription>
            </div>
            <div className="flex flex-wrap items-center gap-2">
              <NativeSelect aria-label="Sort clients" value={sort} onChange={(event) => { setSort(event.target.value as ClientSort); paging.setPage(1); }}>
                <NativeSelectOption value="recent">Recently active</NativeSelectOption>
                <NativeSelectOption value="attention">Needs a nudge first</NativeSelectOption>
                <NativeSelectOption value="workouts">Most workouts</NativeSelectOption>
              </NativeSelect>
              <SearchBox value={search} onChange={(next) => { setSearch(next); paging.setPage(1); }} placeholder="Search by name or email" label="Search clients" />
            </div>
          </CardHeader>
          <CardContent className="space-y-4">
            <FilterToggle label="Filter by training" value={filter} options={options} onChange={(next) => { setFilter(next); paging.setPage(1); }} />
            {rows.length ? (
              <div className="rounded-lg border">
                <Table>
                  <TableHeader>
                    <TableRow>{['Client', 'Training', 'Last workout', 'This week', 'Body weight', 'Eating (last 7 days)'].map((title) => <TableHead key={title}>{title}</TableHead>)}</TableRow>
                  </TableHeader>
                  <TableBody>
                    {rows.slice(paging.start, paging.end).map((client) => (
                      <TableRow key={client.uid}>
                        <TableCell><UserCell user={client} /></TableCell>
                        <TableCell><TrainingBadge stats={client.stats} /></TableCell>
                        <TableCell>{lastWorkoutText(client.stats)}</TableCell>
                        <TableCell><WeekCell stats={client.stats} /></TableCell>
                        <TableCell><WeightCell stats={client.stats} /></TableCell>
                        <TableCell><IntakeCell stats={client.stats} /></TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </div>
            ) : (
              <Empty>{clients.items.length ? 'No client matches that.' : 'No clients yet. A client appears here once they open the new version of the app.'}</Empty>
            )}
            <MoreButton hasMore={clients.hasMore} busy={clients.loadingMore} error={clients.moreError} onClick={clients.loadMore}>Load more clients</MoreButton>
          </CardContent>
          <CardFooter className="border-t">
            <div className="w-full">
              <TablePagination info={paging} size={paging.size} onPage={paging.setPage} onSize={paging.setSize} noun="clients" />
            </div>
          </CardFooter>
        </Card>
      )}
    </Page>
  );
}
