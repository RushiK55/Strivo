'use client';
// The tabs of a client's page. Read-only, apart from sending a plan (Plans tab): a trainer cannot change a client's data,
// and cannot see what the client eats.
import { useCallback, useState, type ReactNode } from 'react';
import { BarSeries, LineSeries } from '@/components/Charts';
import { EventList } from '@/components/EventList';
import { TablePagination } from '@/components/TablePagination';
import { Empty, ErrorBox, FilterToggle, MoreButton, PageSkeleton, Stat, ToneBadge, type FilterOption } from '@/components/ui';
import { Accordion, AccordionContent, AccordionItem, AccordionTrigger } from '@/components/ui/accordion';
import { Badge } from '@/components/ui/badge';
import { Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from '@/components/ui/card';
import { NativeSelect, NativeSelectOption } from '@/components/ui/native-select';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { calendarGrid, exerciseProgress, intakeAverages, weeklyWorkouts, weightTrend, workoutDates } from '@/lib/coaching';
import { getProfile, listEvents, listNutrition, listUserRows, type Cursor } from '@/lib/data';
import { fmtNumber, fmtShortDay } from '@/lib/format';
import { useLoad, usePaged, usePagination } from '@/lib/hooks';
import { describeSets, durationText, eventKinds, eventLabel, parseJsonField, sessionVolume, weightSeries } from '@/lib/logic';
import type { ClientStats, SessionExercise, WeightRow, WorkoutRow } from '@/lib/types';
import { cn } from '@/lib/utils';
import { ClientPlansTab } from './ClientPlansTab';

export const TABS = [
  ['overview', 'Overview'], ['strength', 'Strength'], ['body', 'Body'], ['nutrition', 'Nutrition'], ['workouts', 'Workouts'], ['plans', 'Plans'], ['activity', 'Activity'],
] as const;
export type TabKey = (typeof TABS)[number][0];

const DemoBadge = ({ row }: { row: { isDemo?: number } }) => (row.isDemo ? <Badge variant="secondary">demo data</Badge> : null);

/** A list of expandable rows in one card. */
const Rows = ({ children }: { children: ReactNode }) => <Accordion className="rounded-lg border px-4">{children}</Accordion>;

/** Runs a loader and shows the placeholder, the error or what it returned. */
function Loaded<T>({ load, children }: { load: () => Promise<T>; children: (data: T) => ReactNode }) {
  const { data, error } = useLoad(load);
  if (error) return <ErrorBox error={error} />;
  if (data === undefined) return <PageSkeleton />;
  return <>{children(data)}</>;
}

export interface ClientTabProps {
  tab: TabKey;
  uid: string;
  stats: ClientStats | undefined;
  /** The client's finished workouts, newest first; undefined while they load. */
  workouts: WorkoutRow[] | undefined;
  workoutsError: unknown;
}

export function ClientTab({ tab, uid, stats, workouts, workoutsError }: ClientTabProps) {
  const withWorkouts = (render: (rows: WorkoutRow[]) => ReactNode): ReactNode =>
    workoutsError ? <ErrorBox error={workoutsError} /> : workouts ? render(workouts) : <PageSkeleton />;
  switch (tab) {
    case 'overview': return withWorkouts((rows) => <OverviewTab workouts={rows} stats={stats} />);
    case 'strength': return withWorkouts((rows) => <StrengthTab workouts={rows} />);
    case 'body': return <BodyTab uid={uid} />;
    case 'nutrition': return <NutritionTab uid={uid} />;
    case 'workouts': return withWorkouts((rows) => <WorkoutsTab workouts={rows} />);
    case 'plans': return <ClientPlansTab uid={uid} />;
    case 'activity': return <ActivityTab uid={uid} />;
  }
}

// ---- Overview: attendance ----

function OverviewTab({ workouts, stats }: { workouts: WorkoutRow[]; stats: ClientStats | undefined }) {
  const dates = workoutDates(workouts);
  const weeks = weeklyWorkouts(dates, 12);
  const grid = calendarGrid(dates, 12);
  const lastFour = weeks.slice(-4).reduce((sum, week) => sum + week.count, 0) / 4;
  return (
    <div className="grid items-start gap-4 lg:grid-cols-3">
      <Card className="lg:col-span-2">
        <CardHeader>
          <CardTitle className="text-lg">Workouts per week</CardTitle>
          <CardDescription>The last 12 weeks{workouts.length >= 200 ? ' (the latest 200 workouts)' : ''}.</CardDescription>
        </CardHeader>
        <CardContent>
          <BarSeries data={weeks.map((week) => ({ label: fmtShortDay(week.weekStart), count: week.count }))} xKey="label" yKey="count" label="Workouts" />
        </CardContent>
      </Card>
      <div className="grid gap-4">
        <Stat label="Workouts per week, last 4 weeks" value={lastFour.toFixed(1)} note={stats?.plannedDays ? `plan: ${stats.plannedDays} days a week` : 'no weekly plan'} />
        <Stat label="Workouts recorded" value={workouts.length} />
      </div>
      <Card className="lg:col-span-3">
        <CardHeader>
          <CardTitle className="text-lg">Calendar</CardTitle>
          <CardDescription>Each column is a week, Monday at the top. A filled square is a day with a workout.</CardDescription>
        </CardHeader>
        <CardContent>
          <div className="grid grid-flow-col grid-rows-7 gap-1 sm:max-w-xl" role="img" aria-label="Workout calendar of the last 12 weeks">
            {grid.flat().map((day) => (
              <span
                key={day.date} title={`${day.date}${day.trained ? ': trained' : ''}`}
                className={cn('aspect-square rounded-sm', day.future ? 'bg-transparent' : day.trained ? 'bg-primary' : 'bg-muted')}
              />
            ))}
          </div>
        </CardContent>
      </Card>
    </div>
  );
}

// ---- Strength: how the lifts are developing ----

function StrengthTab({ workouts }: { workouts: WorkoutRow[] }) {
  const progress = exerciseProgress(workouts);
  const [picked, setPicked] = useState<string | undefined>(undefined);
  if (!progress.length) return <Empty>No lifts recorded yet.</Empty>;
  const current = progress.find((exercise) => exercise.name === picked) ?? progress[0]!;
  const change = (value: number, unit: string) => `${value > 0 ? '+' : ''}${value} ${unit}`;
  return (
    <div className="space-y-4">
      <Card>
        <CardHeader className="gap-3 sm:flex sm:items-center sm:justify-between">
          <div>
            <CardTitle className="text-lg">{current.name}</CardTitle>
            <CardDescription>
              {`${current.mode === 'weight' ? 'Heaviest set' : 'Most reps in a set'} of each workout: ${current.first} → ${current.latest} ${current.unit} (${change(current.change, current.unit)}) over ${current.points.length} workouts`}
            </CardDescription>
          </div>
          <NativeSelect aria-label="Exercise" value={current.name} onChange={(event) => setPicked(event.target.value)}>
            {progress.map((exercise) => <NativeSelectOption key={exercise.name} value={exercise.name}>{exercise.name}</NativeSelectOption>)}
          </NativeSelect>
        </CardHeader>
        <CardContent>
          <LineSeries
            data={current.points.map((point) => ({ label: fmtShortDay(point.date), value: current.mode === 'weight' ? point.topKg : point.topReps }))}
            xKey="label" yKey="value" label={current.mode === 'weight' ? 'Heaviest set (kg)' : 'Most reps'}
          />
        </CardContent>
      </Card>
      <Card>
        <CardHeader>
          <CardTitle className="text-lg">All exercises</CardTitle>
          <CardDescription>The most trained first. Click one to see its chart. 1RM is the estimated one-rep max.</CardDescription>
        </CardHeader>
        <CardContent>
          <div className="rounded-lg border">
            <Table>
              <TableHeader>
                <TableRow>{['Exercise', 'Workouts', 'First', 'Latest', 'Change', 'Best 1RM'].map((title) => <TableHead key={title}>{title}</TableHead>)}</TableRow>
              </TableHeader>
              <TableBody>
                {progress.map((exercise) => (
                  <TableRow key={exercise.name} className={cn('cursor-pointer', exercise.name === current.name && 'bg-muted/50')} onClick={() => setPicked(exercise.name)}>
                    <TableCell className="font-semibold">{exercise.name}</TableCell>
                    <TableCell className="tabular-nums">{exercise.points.length}</TableCell>
                    <TableCell className="tabular-nums">{`${exercise.first} ${exercise.unit}`}</TableCell>
                    <TableCell className="tabular-nums">{`${exercise.latest} ${exercise.unit}`}</TableCell>
                    <TableCell><ToneBadge tone={exercise.change > 0 ? 'good' : exercise.change < 0 ? 'warn' : 'neutral'}>{change(exercise.change, exercise.unit)}</ToneBadge></TableCell>
                    <TableCell className="tabular-nums">{exercise.mode === 'weight' ? `${exercise.bestE1rm} kg` : '–'}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </div>
        </CardContent>
      </Card>
    </div>
  );
}

// ---- Body ----

function BodyTab({ uid }: { uid: string }) {
  const load = useCallback(async () => {
    const [profile, weights] = await Promise.all([getProfile(uid), listUserRows<WeightRow>(uid, 'weights', { orderField: 'date', max: 365 })]);
    return { profile, series: weightSeries(weights.filter((row) => !row.isDemo)) };
  }, [uid]);
  return (
    <Loaded load={load}>
      {({ profile, series }) => {
        const trend = weightTrend(series);
        return (
          <div className="space-y-4">
            {profile ? (
              <div className="grid grid-cols-2 gap-4 lg:grid-cols-5">
                <Stat label="Gender" value={profile.gender || '–'} />
                <Stat label="Age" value={profile.age ?? '–'} />
                <Stat label="Height" value={profile.height ? `${profile.height} cm` : '–'} />
                <Stat label="Weight" value={profile.weight ? `${profile.weight} kg` : '–'} />
                <Stat label="Rest days" value={(profile.restDays ?? []).join(', ') || '–'} />
              </div>
            ) : <Empty>No profile yet.</Empty>}
            {series.length ? (
              <Card>
                <CardHeader>
                  <CardTitle className="text-lg">Body weight</CardTitle>
                  <CardDescription>
                    {trend ? `${series[0]!.date}: ${trend.first} kg → ${series.at(-1)!.date}: ${trend.latest} kg (${trend.change > 0 ? '+' : ''}${trend.change} kg, ${series.length} weigh-ins)` : `${series.length} weigh-in`}
                  </CardDescription>
                </CardHeader>
                <CardContent className="grid items-start gap-4 lg:grid-cols-3">
                  <div className="lg:col-span-2">
                    <LineSeries data={series.map((point) => ({ label: fmtShortDay(point.date), kg: point.kg }))} xKey="label" yKey="kg" label="Body weight (kg)" />
                  </div>
                  <div className="rounded-lg border">
                    <Table>
                      <TableBody>
                        {series.slice(-10).reverse().map((point) => <TableRow key={point.date}><TableCell>{point.date}</TableCell><TableCell>{`${point.kg} kg`}</TableCell></TableRow>)}
                      </TableBody>
                    </Table>
                  </div>
                </CardContent>
              </Card>
            ) : <Empty>No weigh-ins yet.</Empty>}
          </div>
        );
      }}
    </Loaded>
  );
}

// ---- Nutrition: totals only ----

function NutritionTab({ uid }: { uid: string }) {
  const load = useCallback(() => listNutrition(uid, 90), [uid]);
  return (
    <Loaded load={load}>
      {(days) => <Nutrition days={days} />}
    </Loaded>
  );
}

function Nutrition({ days }: { days: Awaited<ReturnType<typeof listNutrition>> }) {
  const paging = usePagination(days.length, 10);
  if (!days.length) return <Empty>No daily totals yet. They appear once the client logs food in the app.</Empty>;
  const week = intakeAverages(days, 7);
  const month = intakeAverages(days, 30);
  const chart = days.slice(0, 30).reverse().map((day) => ({ label: fmtShortDay(day.date), calories: day.calories, protein: day.protein ?? 0 }));
  return (
    <div className="space-y-4">
      <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
        <Stat label="Calories, last 7 days" value={week.calories !== null ? `${week.calories.toLocaleString('en-US')} kcal` : '–'} note={`average of ${week.loggedDays} logged ${week.loggedDays === 1 ? 'day' : 'days'}`} />
        <Stat label="Protein, last 7 days" value={week.protein !== null ? `${week.protein} g` : '–'} note="average a day" />
        <Stat label="Calories, last 30 days" value={month.calories !== null ? `${month.calories.toLocaleString('en-US')} kcal` : '–'} note={`average of ${month.loggedDays} logged days`} />
        <Stat label="Protein, last 30 days" value={month.protein !== null ? `${month.protein} g` : '–'} note="average a day" />
      </div>
      <div className="grid gap-4 lg:grid-cols-2">
        <Card>
          <CardHeader><CardTitle className="text-lg">Calories per day</CardTitle><CardDescription>Only days with something logged.</CardDescription></CardHeader>
          <CardContent><BarSeries data={chart} xKey="label" yKey="calories" label="Calories" /></CardContent>
        </Card>
        <Card>
          <CardHeader><CardTitle className="text-lg">Protein per day</CardTitle><CardDescription>Grams, when the client recorded them.</CardDescription></CardHeader>
          <CardContent><BarSeries data={chart} xKey="label" yKey="protein" label="Protein (g)" color={2} /></CardContent>
        </Card>
      </div>
      <Card>
        <CardHeader>
          <CardTitle className="text-lg">Daily totals</CardTitle>
          <CardDescription>You see how much your client eats, not what. The food itself is private to them.</CardDescription>
        </CardHeader>
        <CardContent>
          <div className="rounded-lg border">
            <Table>
              <TableHeader>
                <TableRow>{['Day', 'Calories', 'Protein', 'Carbs', 'Fat', 'Meals'].map((title) => <TableHead key={title}>{title}</TableHead>)}</TableRow>
              </TableHeader>
              <TableBody>
                {days.slice(paging.start, paging.end).map((day) => (
                  <TableRow key={day.id}>
                    <TableCell>{day.date}</TableCell>
                    <TableCell className="tabular-nums">{`${fmtNumber(day.calories)} kcal`}</TableCell>
                    <TableCell className="tabular-nums">{day.protein ? `${day.protein} g` : '–'}</TableCell>
                    <TableCell className="tabular-nums">{day.carbs ? `${day.carbs} g` : '–'}</TableCell>
                    <TableCell className="tabular-nums">{day.fat ? `${day.fat} g` : '–'}</TableCell>
                    <TableCell className="tabular-nums">{day.meals ?? '–'}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </div>
        </CardContent>
        <CardFooter className="border-t">
          <div className="w-full"><TablePagination info={paging} size={paging.size} onPage={paging.setPage} onSize={paging.setSize} noun="days" /></div>
        </CardFooter>
      </Card>
    </div>
  );
}

// ---- Workouts ----

function WorkoutsTab({ workouts }: { workouts: WorkoutRow[] }) {
  const paging = usePagination(workouts.length, 10);
  if (workouts.length === 0) return <Empty>No workouts finished yet.</Empty>;
  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-lg">Finished workouts</CardTitle>
        <CardDescription>Open one to see every set. W is a warm-up, D a drop set, F a failure set.</CardDescription>
      </CardHeader>
      <CardContent>
        <Rows>
          {workouts.slice(paging.start, paging.end).map((session) => {
            const exercises = parseJsonField<SessionExercise[]>(session.exercises, []);
            const volume = sessionVolume(exercises);
            const skipped = parseJsonField<string[]>(session.skipped, []).filter((name): name is string => typeof name === 'string');
            return (
              <AccordionItem key={session.id} value={session.id}>
                <AccordionTrigger>
                  <span className="flex flex-wrap items-center gap-x-1">
                    <strong>{session.planName || 'Workout'}</strong>
                    <span className="text-muted-foreground">{` · ${String(session.date || '').replace('T', ' ').slice(0, 16)}`}</span>
                    <span>{` · ${durationText(session.totalTime) || '–'} · ${exercises.length} ${exercises.length === 1 ? 'exercise' : 'exercises'}`}{volume > 0 ? ` · ${fmtNumber(Math.round(volume))} kg` : ''}</span>
                    {skipped.length ? <span className="text-muted-foreground">{` · skipped ${skipped.length}`}</span> : null}
                    <DemoBadge row={session} />
                  </span>
                </AccordionTrigger>
                <AccordionContent>
                  <Table>
                    <TableHeader><TableRow><TableHead>Exercise</TableHead><TableHead>Sets (W warm-up, D drop, F failure)</TableHead></TableRow></TableHeader>
                    <TableBody>
                      {exercises.map((exercise, index) => (
                        <TableRow key={index}><TableCell>{exercise.name}</TableCell><TableCell>{describeSets(exercise.sets) || '–'}</TableCell></TableRow>
                      ))}
                    </TableBody>
                  </Table>
                  {skipped.length ? <p className="mt-2 text-sm text-muted-foreground">{`Left out of this workout: ${skipped.join(', ')}.`}</p> : null}
                </AccordionContent>
              </AccordionItem>
            );
          })}
        </Rows>
      </CardContent>
      <CardFooter className="border-t">
        <div className="w-full"><TablePagination info={paging} size={paging.size} onPage={paging.setPage} onSize={paging.setSize} noun="workouts" /></div>
      </CardFooter>
    </Card>
  );
}

// ---- Activity ----

function ActivityTab({ uid }: { uid: string }) {
  const fetchPage = useCallback((after: Cursor | null) => listEvents({ uid, after }), [uid]);
  const events = usePaged(fetchPage);
  const [kind, setKind] = useState('all');
  const rows = events.items.filter((event) => kind === 'all' || eventLabel(event.type) === kind);
  const paging = usePagination(rows.length, 25);
  if (events.error) return <ErrorBox error={events.error} />;
  if (events.loading) return <PageSkeleton />;
  const options: FilterOption<string>[] = [{ value: 'all', label: 'Everything' }, ...eventKinds(events.items).map((label) => ({ value: label, label }))];
  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-lg">Activity</CardTitle>
        <CardDescription>{`${events.items.length} loaded${events.hasMore ? ', older activity available' : ''}`}</CardDescription>
      </CardHeader>
      <CardContent className="space-y-4">
        <FilterToggle label="Filter by kind" value={kind} options={options} onChange={(next) => { setKind(next); paging.setPage(1); }} />
        <EventList events={rows.slice(paging.start, paging.end)} />
        <MoreButton hasMore={events.hasMore} busy={events.loadingMore} error={events.moreError} onClick={events.loadMore}>Load older activity</MoreButton>
      </CardContent>
      <CardFooter className="border-t">
        <div className="w-full"><TablePagination info={paging} size={paging.size} onPage={paging.setPage} onSize={paging.setSize} noun="actions" /></div>
      </CardFooter>
    </Card>
  );
}
