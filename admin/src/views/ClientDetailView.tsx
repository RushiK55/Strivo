'use client';
// One client, as their trainer sees them: attendance, strength and body-weight progress, plan, and daily calories and
// protein. Read-only. The client comes from the address (?uid=...), because a static site cannot have a page for every
// account id.
import { ArrowLeft, CalendarDays, Dumbbell, Flame, Scale, Smartphone, Target, Timer } from 'lucide-react';
import Link from 'next/link';
import { useSearchParams } from 'next/navigation';
import { Suspense, useCallback, useState } from 'react';
import { TrainingBadge } from '@/components/ClientParts';
import { UserAvatar } from '@/components/UserAvatar';
import { Empty, ErrorBox, Page, PageSkeleton, Stat } from '@/components/ui';
import { Button } from '@/components/ui/button';
import { Card, CardContent } from '@/components/ui/card';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { adherence, lastWorkoutText } from '@/lib/coaching';
import { getUser, listUserRows } from '@/lib/data';
import { fmtDate } from '@/lib/format';
import { useLoad } from '@/lib/hooks';
import type { WorkoutRow } from '@/lib/types';
import { TABS, ClientTab, type TabKey } from './ClientTabs';

const Back = () => (
  <Button variant="ghost" size="sm" className="-ml-2 w-fit text-muted-foreground" render={<Link href="/clients/" />} nativeButton={false}><ArrowLeft /> Clients</Button>
);

export function ClientDetailView() {
  return (
    <Suspense fallback={<Page title="Client"><PageSkeleton /></Page>}>
      <FromAddress />
    </Suspense>
  );
}

function FromAddress() {
  const uid = useSearchParams().get('uid');
  if (!uid) return <Page title="Client"><Back /><Empty>Pick a client from the Clients page.</Empty></Page>;
  return <ClientPage key={uid} uid={uid} />;
}

function ClientPage({ uid }: { uid: string }) {
  const loadUser = useCallback(() => getUser(uid), [uid]);
  // The finished workouts feed three tabs and the figures above them, so they are read once here.
  const loadWorkouts = useCallback(() => listUserRows<WorkoutRow>(uid, 'sessions', { orderField: 'date', max: 200 }).then((rows) => rows.filter((row) => !row.isDemo)), [uid]);
  const { data: user, error } = useLoad(loadUser);
  const workouts = useLoad(loadWorkouts);
  const [tab, setTab] = useState<TabKey>('overview');

  if (error) return <Page title="Client"><Back /><ErrorBox error={error} /></Page>;
  if (user === undefined) return <Page title="Client"><PageSkeleton stats={4} /></Page>;
  if (user === null) {
    return <Page title="Client"><Back /><Empty>That client has no summary yet. It appears once they open the new version of the app.</Empty></Page>;
  }

  const stats = user.stats;
  const plan = adherence(stats);
  return (
    <div className="mx-auto w-full max-w-7xl space-y-6 p-4 md:p-8">
      <Back />
      <Card>
        <CardContent className="flex flex-wrap items-center gap-4">
          <UserAvatar name={user.name} email={user.email} size="lg" />
          <div className="min-w-0 flex-1">
            <div className="flex flex-wrap items-center gap-2">
              <h1>{user.name || '(no name)'}</h1>
              <TrainingBadge stats={stats} />
            </div>
            <p className="truncate text-muted-foreground">{user.email || uid}</p>
          </div>
          <dl className="flex flex-wrap gap-x-6 gap-y-2 text-sm">
            <div className="flex items-center gap-2"><Timer className="size-4 text-muted-foreground" aria-hidden="true" /><dt className="sr-only">Last workout</dt><dd>{`Last workout: ${lastWorkoutText(stats)}`}</dd></div>
            <div className="flex items-center gap-2"><CalendarDays className="size-4 text-muted-foreground" aria-hidden="true" /><dt className="sr-only">Joined</dt><dd>{`Joined ${fmtDate(user.createdAt)}`}</dd></div>
            <div className="flex items-center gap-2"><Smartphone className="size-4 text-muted-foreground" aria-hidden="true" /><dt className="sr-only">App</dt><dd>{`app ${user.appVersion || '–'}`}</dd></div>
          </dl>
        </CardContent>
      </Card>

      {stats ? (
        <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
          <Stat icon={Target} label="This week" value={stats.plannedDays ? `${stats.workouts7d} of ${stats.plannedDays}` : stats.workouts7d} note={plan !== null ? `${plan}% of the plan` : 'no weekly plan'} />
          <Stat icon={Dumbbell} label="Workouts, last 30 days" value={stats.workouts30d} />
          <Stat icon={Scale} label="Body weight" value={stats.weightKg !== undefined ? `${stats.weightKg} kg` : '–'} note={stats.weightChange30dKg ? `${stats.weightChange30dKg > 0 ? '+' : ''}${stats.weightChange30dKg} kg in 30 days` : undefined} />
          <Stat icon={Flame} label="Eating, last 7 days" value={stats.avgCalories7d !== undefined ? `${stats.avgCalories7d.toLocaleString('en-US')} kcal` : '–'} note={stats.avgProteinG7d !== undefined ? `${stats.avgProteinG7d} g protein a day` : undefined} />
        </div>
      ) : (
        <Empty>This client has not opened a version of the app that reports training yet. Their workouts and plans below are still shown.</Empty>
      )}

      <Tabs value={tab} onValueChange={(value) => setTab(value as TabKey)}>
        <TabsList variant="line" className="w-full justify-start overflow-x-auto border-b">
          {TABS.map(([key, label]) => <TabsTrigger key={key} value={key} className="flex-none px-3.5">{label}</TabsTrigger>)}
        </TabsList>
        <TabsContent value={tab} className="pt-4">
          <ClientTab key={tab} tab={tab} uid={uid} stats={stats} workouts={workouts.data} workoutsError={workouts.error} />
        </TabsContent>
      </Tabs>
    </div>
  );
}
