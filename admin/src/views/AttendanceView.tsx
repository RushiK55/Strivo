'use client';
// Who came and who did not: every client's last 14 days as a row of squares, this week against the plan, and the
// last workout. Built from the finished workouts of all clients (one query), so it is exact for the days it shows.
import { CalendarCheck, CalendarX, Dumbbell, Users } from 'lucide-react';
import { useCallback, useState } from 'react';
import { TrainingBadge } from '@/components/ClientParts';
import { UserCell } from '@/components/EventList';
import { TablePagination } from '@/components/TablePagination';
import { Empty, ErrorBox, FilterToggle, Page, PageSkeleton, SearchBox, Stat, type FilterOption } from '@/components/ui';
import { Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from '@/components/ui/card';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { dayStrip, lastWorkoutText, sortClients, workoutDates } from '@/lib/coaching';
import { listUsers, listWorkoutsSince } from '@/lib/data';
import { useLoad, usePagination } from '@/lib/hooks';
import { matchesUser } from '@/lib/logic';
import { rememberUsers } from '@/lib/names';
import type { UserSummary } from '@/lib/types';
import { cn } from '@/lib/utils';

const DAY = 24 * 60 * 60 * 1000;
const DAYS = 14;
type Filter = 'all' | 'missed' | 'below' | 'met';

interface Row { client: UserSummary; strip: { date: string; trained: boolean }[]; week: number }

export function AttendanceView() {
  const load = useCallback(async () => {
    const now = Date.now();
    const since = new Date(now - (DAYS - 1) * DAY).toISOString().slice(0, 10);
    const [people, workouts] = await Promise.all([listUsers(), listWorkoutsSince(since)]);
    rememberUsers(people.items);
    const byClient = new Map<string, string[]>();
    for (const workout of workouts) byClient.set(workout.uid, [...(byClient.get(workout.uid) ?? []), ...workoutDates([workout])]);
    const rows: Row[] = sortClients(people.items, 'attention').map((client) => {
      const strip = dayStrip(byClient.get(client.uid) ?? [], DAYS, now);
      return { client, strip, week: strip.slice(-7).filter((day) => day.trained).length };
    });
    return { now, rows, partial: people.hasMore };
  }, []);
  const { data, error } = useLoad(load);
  const [search, setSearch] = useState('');
  const [filter, setFilter] = useState<Filter>('all');

  const rows = data?.rows ?? [];
  const planned = (row: Row): number => row.client.stats?.plannedDays ?? 0;
  const matches: Record<Filter, (row: Row) => boolean> = {
    all: () => true,
    missed: (row) => row.week === 0,
    below: (row) => planned(row) > 0 && row.week > 0 && row.week < planned(row),
    met: (row) => planned(row) > 0 && row.week >= planned(row),
  };
  const shown = rows.filter((row) => matches[filter](row) && matchesUser(row.client, search));
  const paging = usePagination(shown.length, 10);

  const options: FilterOption<Filter>[] = [
    { value: 'all', label: 'All', count: rows.length },
    { value: 'missed', label: 'Missed this week', count: rows.filter(matches.missed).length },
    { value: 'below', label: 'Below plan', count: rows.filter(matches.below).length },
    { value: 'met', label: 'Met the plan', count: rows.filter(matches.met).length },
  ];
  const trained = rows.filter((row) => row.week > 0).length;
  const total = rows.reduce((sum, row) => sum + row.week, 0);

  return (
    <Page title="Attendance" description="Who came to train in the last two weeks, and who did not.">
      {error ? <ErrorBox error={error} /> : null}
      {!data ? <PageSkeleton stats={4} /> : (
        <>
          <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
            <Stat icon={Users} label="Clients" value={rows.length} note={data.partial ? 'the most recently active' : undefined} />
            <Stat icon={CalendarCheck} label="Trained in the last 7 days" value={trained} />
            <Stat icon={CalendarX} label="Missed the whole week" value={rows.length - trained} />
            <Stat icon={Dumbbell} label="Workouts per client, this week" value={rows.length ? (total / rows.length).toFixed(1) : '–'} />
          </div>
          <Card>
            <CardHeader className="gap-3 sm:flex sm:items-center sm:justify-between">
              <div>
                <CardTitle className="text-lg">Last 14 days</CardTitle>
                <CardDescription>A filled square is a day with a finished workout. The last square is today.</CardDescription>
              </div>
              <SearchBox value={search} onChange={(next) => { setSearch(next); paging.setPage(1); }} placeholder="Search by name or email" label="Search clients" />
            </CardHeader>
            <CardContent className="space-y-4">
              <FilterToggle label="Filter by attendance" value={filter} options={options} onChange={(next) => { setFilter(next); paging.setPage(1); }} />
              {shown.length ? (
                <div className="rounded-lg border">
                  <Table>
                    <TableHeader>
                      <TableRow>{['Client', 'Last 14 days', 'This week', 'Last workout', 'Training'].map((title) => <TableHead key={title}>{title}</TableHead>)}</TableRow>
                    </TableHeader>
                    <TableBody>
                      {shown.slice(paging.start, paging.end).map((row) => (
                        <TableRow key={row.client.uid}>
                          <TableCell><UserCell user={row.client} /></TableCell>
                          <TableCell>
                            <div className="flex gap-1" role="img" aria-label={`${row.strip.filter((day) => day.trained).length} workouts in 14 days`}>
                              {row.strip.map((day) => (
                                <span key={day.date} title={`${day.date}${day.trained ? ': trained' : ''}`} className={cn('size-4 rounded-sm', day.trained ? 'bg-primary' : 'bg-muted')} />
                              ))}
                            </div>
                          </TableCell>
                          <TableCell className="tabular-nums">{planned(row) ? `${row.week} of ${planned(row)} days` : `${row.week} ${row.week === 1 ? 'day' : 'days'}`}</TableCell>
                          <TableCell>{lastWorkoutText(row.client.stats, data.now)}</TableCell>
                          <TableCell><TrainingBadge stats={row.client.stats} /></TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                </div>
              ) : (
                <Empty>{rows.length ? 'No client matches that.' : 'No clients yet.'}</Empty>
              )}
            </CardContent>
            <CardFooter className="border-t">
              <div className="w-full"><TablePagination info={paging} size={paging.size} onPage={paging.setPage} onSize={paging.setSize} noun="clients" /></div>
            </CardFooter>
          </Card>
        </>
      )}
    </Page>
  );
}
