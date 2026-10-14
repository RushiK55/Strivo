// What a trainer wants to know about a client, worked out from the data the dashboard may read: who is coming, how the
// lifts and body weight are developing, and how much they eat (totals only: the dashboard never sees what was eaten).
// Pure functions, no React and no Firebase. Covered by coaching.test.ts.
import { parseJsonField, relativeTime, workSets } from './logic';
import type { ClientStats, NutritionDay, SessionExercise, Tone, UserSummary } from './types';

const DAY = 24 * 60 * 60 * 1000;

// ---- Attendance ----

/** Whether a client is training: on track (a workout in the last 3 days), slipping (4-7), missing (more), or has not started. */
export type TrainingStatus = 'ontrack' | 'slipping' | 'missing' | 'never' | 'unknown';

export const TRAINING_LABEL: Record<TrainingStatus, string> = {
  ontrack: 'On track', slipping: 'Slipping', missing: 'Missing', never: 'No workouts yet', unknown: 'No data yet',
};
export const TRAINING_TONE: Record<TrainingStatus, Tone> = { ontrack: 'good', slipping: 'warn', missing: 'bad', never: 'neutral', unknown: 'neutral' };

/** `unknown` means the client has not opened a version of the app that reports training yet. */
export function trainingStatus(stats: ClientStats | undefined, now: number = Date.now()): TrainingStatus {
  if (!stats) return 'unknown';
  if (!stats.lastWorkoutAt) return 'never';
  const days = Math.floor((now - stats.lastWorkoutAt) / DAY);
  return days <= 3 ? 'ontrack' : days <= 7 ? 'slipping' : 'missing';
}

/** "3 h ago", "yesterday", "9 days ago", or "never". */
export const lastWorkoutText = (stats: ClientStats | undefined, now: number = Date.now()): string =>
  stats?.lastWorkoutAt ? relativeTime(stats.lastWorkoutAt, now) : stats ? 'never' : '–';

/** This week's workouts against the days the client planned, as a percentage (at most 100); null without a plan. */
export function adherence(stats: Pick<ClientStats, 'workouts7d' | 'plannedDays'> | undefined): number | null {
  if (!stats || !stats.plannedDays) return null;
  return Math.min(100, Math.round((stats.workouts7d / stats.plannedDays) * 100));
}

const dateKey = (ms: number): string => new Date(ms).toISOString().slice(0, 10);
const dateMs = (date: string): number => Date.parse(`${date}T00:00:00Z`);

/** The calendar days of workouts: the date part of each workout's date (written as 2026-10-12T07:30:00 by the phone). */
export function workoutDates(workouts: { date?: string }[]): string[] {
  return workouts.map((workout) => String(workout.date ?? '').slice(0, 10)).filter((date) => /^\d{4}-\d{2}-\d{2}$/.test(date));
}

/** The last `days` days, oldest first, each marked when there was a workout that day. */
export function dayStrip(dates: Iterable<string>, days: number, now: number = Date.now()): { date: string; trained: boolean }[] {
  const trained = new Set(dates);
  return Array.from({ length: days }, (_, i) => {
    const date = dateKey(now - (days - 1 - i) * DAY);
    return { date, trained: trained.has(date) };
  });
}

/** Workouts per week (Monday to Sunday) for the last `weeks` weeks, oldest first; the last entry is the current week. */
export function weeklyWorkouts(dates: string[], weeks: number, now: number = Date.now()): { weekStart: string; count: number }[] {
  const weekStart = (ms: number): number => {
    const midnight = Math.floor(ms / DAY) * DAY;
    return midnight - ((new Date(midnight).getUTCDay() + 6) % 7) * DAY;
  };
  const counts = new Map<number, number>();
  for (const date of dates) counts.set(weekStart(dateMs(date)), (counts.get(weekStart(dateMs(date))) ?? 0) + 1);
  const thisWeek = weekStart(now);
  return Array.from({ length: weeks }, (_, i) => {
    const start = thisWeek - (weeks - 1 - i) * 7 * DAY;
    return { weekStart: dateKey(start), count: counts.get(start) ?? 0 };
  });
}

// ---- Strength progression ----

export interface StrengthPoint {
  date: string;
  /** The heaviest work set (kg), and the reps done at that weight. */
  topKg: number;
  topReps: number;
  /** Estimated one-rep max of the best set (Epley: weight x (1 + reps / 30)). */
  e1rm: number;
  volume: number;
}

export interface ExerciseProgress {
  name: string;
  points: StrengthPoint[];
  /** weight: the heaviest set is tracked; reps: a bodyweight exercise, where the most reps is. */
  mode: 'weight' | 'reps';
  unit: 'kg' | 'reps';
  first: number;
  latest: number;
  change: number;
  bestE1rm: number;
}

const round1 = (value: number): number => Math.round(value * 10) / 10;

/** The best work set of an exercise in one workout, or null when it has none that were done. */
function bestOfSets(sets: SessionExercise['sets']): Omit<StrengthPoint, 'date'> | null {
  const done = workSets(sets).filter((set) => set.isCompleted !== false && Number(set.reps) > 0);
  if (!done.length) return null;
  const weights = done.map((set) => Number(set.weight) || 0);
  const topKg = Math.max(...weights);
  const topReps = Math.max(...done.filter((set) => (Number(set.weight) || 0) === topKg).map((set) => Number(set.reps)));
  const e1rm = Math.max(...done.map((set) => (Number(set.weight) || 0) * (1 + Number(set.reps) / 30)));
  const volume = done.reduce((sum, set) => sum + (Number(set.weight) || 0) * Number(set.reps), 0);
  return { topKg, topReps, e1rm: round1(e1rm), volume };
}

/** Every exercise a client has done, with how its best set developed from workout to workout. Most trained first. */
export function exerciseProgress(workouts: { date?: string; exercises?: string | SessionExercise[] }[]): ExerciseProgress[] {
  const byName = new Map<string, { name: string; points: StrengthPoint[] }>();
  const ordered = [...workouts].sort((a, b) => String(a.date ?? '').localeCompare(String(b.date ?? '')));
  for (const workout of ordered) {
    const date = String(workout.date ?? '').slice(0, 10);
    for (const exercise of parseJsonField<SessionExercise[]>(workout.exercises, [])) {
      const best = bestOfSets(exercise.sets);
      const name = String(exercise.name ?? '').trim();
      if (!best || !name) continue;
      const entry = byName.get(name.toLowerCase()) ?? { name, points: [] };
      entry.points.push({ date, ...best });
      byName.set(name.toLowerCase(), entry);
    }
  }
  return [...byName.values()]
    .map(({ name, points }): ExerciseProgress => {
      const mode = points.some((point) => point.topKg > 0) ? 'weight' : 'reps';
      const metric = (point: StrengthPoint): number => (mode === 'weight' ? point.topKg : point.topReps);
      const first = metric(points[0]!);
      const latest = metric(points[points.length - 1]!);
      return { name, points, mode, unit: mode === 'weight' ? 'kg' : 'reps', first, latest, change: round1(latest - first), bestE1rm: Math.max(...points.map((point) => point.e1rm)) };
    })
    .sort((a, b) => b.points.length - a.points.length || a.name.localeCompare(b.name));
}

// ---- Body weight and intake ----

/** First and latest weigh-in of a series (oldest first) and the change between them; null with fewer than two. */
export function weightTrend(series: { date: string; kg: number }[]): { first: number; latest: number; change: number } | null {
  const first = series[0];
  const latest = series[series.length - 1];
  if (!first || !latest || series.length < 2) return null;
  return { first: first.kg, latest: latest.kg, change: round1(latest.kg - first.kg) };
}

/** Average calories and protein over the days in the last `days` days that have something logged. */
export function intakeAverages(days: NutritionDay[], count: number, now: number = Date.now()): { loggedDays: number; calories: number | null; protein: number | null } {
  const since = dateKey(now - (count - 1) * DAY);
  const recent = days.filter((day) => day.date >= since && day.date <= dateKey(now));
  const withProtein = recent.filter((day) => Number(day.protein) > 0);
  const mean = (values: number[]): number | null => (values.length ? Math.round(values.reduce((a, b) => a + b, 0) / values.length) : null);
  return {
    loggedDays: recent.length,
    calories: mean(recent.map((day) => Number(day.calories) || 0)),
    protein: mean(withProtein.map((day) => Number(day.protein))),
  };
}

// ---- The client list ----

export type ClientSort = 'recent' | 'attention' | 'workouts';

/** Clients in the order a trainer asks for: recently active, the ones who need a nudge first, or the most workouts. */
export function sortClients<T extends Pick<UserSummary, 'lastActiveAt' | 'stats'>>(clients: T[], sort: ClientSort): T[] {
  const lastWorkout = (client: T): number => client.stats?.lastWorkoutAt ?? 0;
  const copy = [...clients];
  if (sort === 'attention') {
    // The longest since a workout first; a client with no workouts at all is the one who most needs a nudge, but one
    // without the new app yet (no stats) goes last because nothing is known about them.
    const rank = (client: T): number => (!client.stats ? Number.POSITIVE_INFINITY : lastWorkout(client) === 0 ? -1 : lastWorkout(client));
    return copy.sort((a, b) => rank(a) - rank(b));
  }
  if (sort === 'workouts') return copy.sort((a, b) => (b.stats?.workouts30d ?? -1) - (a.stats?.workouts30d ?? -1));
  return copy.sort((a, b) => (b.lastActiveAt ?? 0) - (a.lastActiveAt ?? 0));
}

/** Workouts per day for the last `days` days, oldest first, with zero for days nobody trained. */
export function workoutsPerDay(dates: string[], days: number, now: number = Date.now()): { date: string; count: number }[] {
  const counts = new Map<string, number>();
  for (const date of dates) counts.set(date, (counts.get(date) ?? 0) + 1);
  return Array.from({ length: days }, (_, i) => {
    const date = dateKey(now - (days - 1 - i) * DAY);
    return { date, count: counts.get(date) ?? 0 };
  });
}

export interface CalendarDay { date: string; trained: boolean; future: boolean }

/** `weeks` weeks of days for a calendar: each week Monday to Sunday, the last one being the current week. */
export function calendarGrid(dates: Iterable<string>, weeks: number, now: number = Date.now()): CalendarDay[][] {
  const trained = new Set(dates);
  const today = dateKey(now);
  const midnight = Math.floor(now / DAY) * DAY;
  const thisMonday = midnight - ((new Date(midnight).getUTCDay() + 6) % 7) * DAY;
  return Array.from({ length: weeks }, (_, w) =>
    Array.from({ length: 7 }, (_, d) => {
      const date = dateKey(thisMonday - (weeks - 1 - w) * 7 * DAY + d * DAY);
      return { date, trained: trained.has(date), future: date > today };
    }));
}
