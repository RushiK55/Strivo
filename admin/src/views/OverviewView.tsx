'use client';
// The first page, for the owner or trainer: how many clients came this week, who needs a nudge, and the latest workouts.
import { CalendarCheck, Dumbbell, TriangleAlert, UserCheck, Users } from 'lucide-react';
import Link from 'next/link';
import { useCallback } from 'react';
import { BarSeries } from '@/components/Charts';
import { TrainingBadge } from '@/components/ClientParts';
import { UserCell, userHref } from '@/components/EventList';
import { Empty, ErrorBox, Page, PageSkeleton, Stat } from '@/components/ui';
import { Button } from '@/components/ui/button';
import { Card, CardAction, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { lastWorkoutText, sortClients, trainingStatus, workoutDates, workoutsPerDay } from '@/lib/coaching';
import { countUsers, listUsers, listWorkoutsSince } from '@/lib/data';
import { fmtNumber, fmtShortDay } from '@/lib/format';
import { useLoad } from '@/lib/hooks';
import { durationText, parseJsonField, relativeTime, sessionVolume } from '@/lib/logic';
import { loadNames, nameOf, rememberUsers } from '@/lib/names';
import type { ClientWorkout, SessionExercise, UserSummary } from '@/lib/types';

const DAY = 24 * 60 * 60 * 1000;
const isoDay = (ms: number): string => new Date(ms).toISOString().slice(0, 10);

interface OverviewData {
  now: number;
  /** All clients, counted on the server; `clients` below are only the most recently active page of them. */
  total: number;
  clients: UserSummary[];
  partial: boolean;
  recent: ClientWorkout[];
  perDay: { date: string; count: number; label: string }[];
  thisWeek: number;
  failure: unknown;
}

export function OverviewView() {
  const load = useCallback(async (): Promise<OverviewData> => {
    const now = Date.now();
    const [total, people, workouts] = await Promise.allSettled([countUsers(), listUsers(), listWorkoutsSince(isoDay(now - 13 * DAY))]);
    const clients = people.status === 'fulfilled' ? people.value : { items: [], hasMore: false, last: null };
    const recent = workouts.status === 'fulfilled' ? workouts.value : [];
    rememberUsers(clients.items);
    await loadNames(recent.slice(0, 8).map((workout) => workout.uid));
    const failed = [total, people, workouts].find((result) => result.status === 'rejected');
    return {
      now,
      total: total.status === 'fulfilled' ? total.value : clients.items.length,
      clients: clients.items,
      partial: clients.hasMore,
      recent,
      perDay: workoutsPerDay(workoutDates(recent), 14, now).map((day) => ({ ...day, label: fmtShortDay(day.date) })),
      thisWeek: recent.filter((workout) => String(workout.date ?? '').slice(0, 10) >= isoDay(now - 6 * DAY)).length,
      failure: failed?.status === 'rejected' ? failed.reason : undefined,
    };
  }, []);
  const { data, error } = useLoad(load);

  return (
    <Page title="Overview" description="How your clients are training this week.">
      {error ? <ErrorBox error={error} /> : null}
      {!data ? <PageSkeleton stats={4} /> : <Body data={data} />}
    </Page>
  );
}

function Body({ data }: { data: OverviewData }) {
  const { now, clients } = data;
  const trained = clients.filter((client) => (client.stats?.workouts7d ?? 0) > 0).length;
  const needNudge = sortClients(clients, 'attention').filter((client) => ['slipping', 'missing', 'never'].includes(trainingStatus(client.stats, now)));
  const mostActive = sortClients(clients, 'workouts').filter((client) => (client.stats?.workouts30d ?? 0) > 0).slice(0, 5);
  const share = clients.length ? `${Math.round((trained / clients.length) * 100)}% of clients${data.partial ? ` (the ${clients.length} most recently active)` : ''}` : undefined;
  return (
    <>
      {data.failure ? <ErrorBox error={data.failure} /> : null}
      <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
        <Stat icon={Users} label="Clients" value={fmtNumber(data.total)} />
        <Stat icon={UserCheck} label="Trained in the last 7 days" value={fmtNumber(trained)} note={share} />
        <Stat icon={Dumbbell} label="Workouts in the last 7 days" value={fmtNumber(data.thisWeek)} />
        <Stat icon={TriangleAlert} label="Need a nudge" value={fmtNumber(needNudge.length)} note="no workout for 4 days or more" />
      </div>

      <div className="grid items-start gap-4 lg:grid-cols-3">
        <Card className="lg:col-span-2">
          <CardHeader>
            <CardTitle className="text-lg">Workouts per day</CardTitle>
            <CardDescription>Finished workouts of all clients, the last 14 days.</CardDescription>
          </CardHeader>
          <CardContent>
            <BarSeries data={data.perDay} xKey="label" yKey="count" label="Workouts" />
          </CardContent>
        </Card>
        <Card>
          <CardHeader>
            <CardTitle className="text-lg">Needs a nudge</CardTitle>
            <CardDescription>Clients who have not trained for a while.</CardDescription>
          </CardHeader>
          <CardContent>
            {needNudge.length ? (
              <ul className="divide-y">
                {needNudge.slice(0, 6).map((client) => (
                  <li key={client.uid} className="flex items-center justify-between gap-3 py-2.5">
                    <UserCell user={client} />
                    <span className="flex flex-none flex-col items-end gap-1">
                      <TrainingBadge stats={client.stats} />
                      <span className="text-xs text-muted-foreground">{`last: ${lastWorkoutText(client.stats, now)}`}</span>
                    </span>
                  </li>
                ))}
              </ul>
            ) : <p className="text-muted-foreground">Everyone is on track.</p>}
          </CardContent>
        </Card>
      </div>

      <div className="grid items-start gap-4 lg:grid-cols-3">
        <Card className="lg:col-span-2">
          <CardHeader>
            <CardTitle className="text-lg">Latest workouts</CardTitle>
            <CardAction><Button variant="ghost" size="sm" render={<Link href="/attendance/" />} nativeButton={false}><CalendarCheck /> Attendance</Button></CardAction>
          </CardHeader>
          <CardContent>
            {data.recent.length ? (
              <ul className="divide-y">
                {data.recent.slice(0, 8).map((workout) => {
                  const volume = sessionVolume(parseJsonField<SessionExercise[]>(workout.exercises, []));
                  return (
                    <li key={`${workout.uid}/${workout.id}`} className="flex flex-wrap items-center justify-between gap-2 py-2.5">
                      <span>
                        <Link href={userHref(workout.uid)} className="font-semibold text-primary hover:underline">{nameOf(workout.uid)}</Link>
                        {` did ${workout.planName || 'a workout'}`}
                        <span className="text-muted-foreground">{` · ${durationText(workout.totalTime) || '–'}${volume > 0 ? ` · ${fmtNumber(Math.round(volume))} kg lifted` : ''}`}</span>
                      </span>
                      <span className="text-xs text-muted-foreground">{relativeTime(Date.parse(String(workout.date ?? '')), now)}</span>
                    </li>
                  );
                })}
              </ul>
            ) : <Empty>No workouts in the last 14 days.</Empty>}
          </CardContent>
        </Card>
        <Card>
          <CardHeader>
            <CardTitle className="text-lg">Most workouts</CardTitle>
            <CardDescription>The last 30 days.</CardDescription>
          </CardHeader>
          <CardContent>
            {mostActive.length ? (
              <ul className="divide-y">
                {mostActive.map((client) => (
                  <li key={client.uid} className="flex items-center justify-between gap-3 py-2.5">
                    <UserCell user={client} />
                    <span className="flex-none font-semibold tabular-nums">{client.stats?.workouts30d}</span>
                  </li>
                ))}
              </ul>
            ) : <p className="text-muted-foreground">No workouts recorded yet.</p>}
          </CardContent>
        </Card>
      </div>
    </>
  );
}
