import { describe, expect, it } from 'vitest';
import {
  adherence, calendarGrid, dayStrip, exerciseProgress, intakeAverages, lastWorkoutText, sortClients, trainingStatus, weeklyWorkouts, weightTrend, workoutDates, workoutsPerDay,
} from './coaching';
import type { ClientStats } from './types';

const NOW = Date.UTC(2026, 9, 14, 12, 0, 0); // Wednesday 2026-10-14
const DAY = 24 * 60 * 60 * 1000;
const stats = (over: Partial<ClientStats> = {}): ClientStats => ({ workouts7d: 0, workouts30d: 0, plannedDays: 0, ...over });

describe('attendance', () => {
  it('says whether a client is training', () => {
    expect(trainingStatus(undefined, NOW)).toBe('unknown');
    expect(trainingStatus(stats(), NOW)).toBe('never');
    expect(trainingStatus(stats({ lastWorkoutAt: NOW - 3 * DAY }), NOW)).toBe('ontrack');
    expect(trainingStatus(stats({ lastWorkoutAt: NOW - 4 * DAY }), NOW)).toBe('slipping');
    expect(trainingStatus(stats({ lastWorkoutAt: NOW - 7 * DAY }), NOW)).toBe('slipping');
    expect(trainingStatus(stats({ lastWorkoutAt: NOW - 8 * DAY }), NOW)).toBe('missing');
  });

  it('writes the last workout the way people say it', () => {
    expect(lastWorkoutText(stats({ lastWorkoutAt: NOW - 3 * DAY }), NOW)).toBe('3 days ago');
    expect(lastWorkoutText(stats(), NOW)).toBe('never');
    expect(lastWorkoutText(undefined, NOW)).toBe('–');
  });

  it('compares this week with the plan, capped at 100%', () => {
    expect(adherence(stats({ workouts7d: 2, plannedDays: 4 }))).toBe(50);
    expect(adherence(stats({ workouts7d: 6, plannedDays: 4 }))).toBe(100);
    expect(adherence(stats({ workouts7d: 3, plannedDays: 0 }))).toBeNull();
    expect(adherence(undefined)).toBeNull();
  });

  it('marks the days with a workout', () => {
    const dates = workoutDates([{ date: '2026-10-14T07:30:00' }, { date: '2026-10-12T18:00:00' }, { date: 'oops' }, {}]);
    expect(dates).toEqual(['2026-10-14', '2026-10-12']);
    expect(dayStrip(dates, 4, NOW)).toEqual([
      { date: '2026-10-11', trained: false },
      { date: '2026-10-12', trained: true },
      { date: '2026-10-13', trained: false },
      { date: '2026-10-14', trained: true },
    ]);
  });

  it('counts workouts per week, Monday to Sunday', () => {
    // 2026-10-12 is a Monday, so the 11th (a Sunday) belongs to the week before.
    const weeks = weeklyWorkouts(['2026-10-14', '2026-10-12', '2026-10-11', '2026-10-05', '2026-10-05'], 3, NOW);
    expect(weeks).toEqual([
      { weekStart: '2026-09-28', count: 0 },
      { weekStart: '2026-10-05', count: 3 },
      { weekStart: '2026-10-12', count: 2 },
    ]);
  });
});

describe('attendance charts', () => {
  it('counts workouts per day across clients, with zero for quiet days', () => {
    expect(workoutsPerDay(['2026-10-14', '2026-10-14', '2026-10-12'], 3, NOW)).toEqual([
      { date: '2026-10-12', count: 1 },
      { date: '2026-10-13', count: 0 },
      { date: '2026-10-14', count: 2 },
    ]);
  });

  it('lays out weeks Monday to Sunday for a calendar, ending with this week', () => {
    const grid = calendarGrid(['2026-10-14', '2026-10-05'], 2, NOW);
    expect(grid).toHaveLength(2);
    expect(grid[0]!.map((d) => d.date)).toEqual(['2026-10-05', '2026-10-06', '2026-10-07', '2026-10-08', '2026-10-09', '2026-10-10', '2026-10-11']);
    expect(grid[1]!.map((d) => d.date)[0]).toBe('2026-10-12');
    expect(grid[0]![0]).toEqual({ date: '2026-10-05', trained: true, future: false });
    expect(grid[1]![2]).toEqual({ date: '2026-10-14', trained: true, future: false });
    expect(grid[1]![3]).toEqual({ date: '2026-10-15', trained: false, future: true });
  });
});

describe('strength progression', () => {
  const session = (date: string, exercises: unknown) => ({ date, exercises: JSON.stringify(exercises) });
  const set = (weight: number, reps: number, extra: object = {}) => ({ weight, reps, isCompleted: true, ...extra });

  it('follows the heaviest work set of each exercise from workout to workout', () => {
    const progress = exerciseProgress([
      session('2026-10-12T07:00:00', [{ name: 'Bench Press', sets: [set(40, 10, { type: 'warmup' }), set(60, 8), set(60, 7)] }]),
      session('2026-10-05T07:00:00', [{ name: 'Bench Press', sets: [set(55, 8), set(55, 8)] }]),
      session('2026-10-14T07:00:00', [{ name: 'bench press', sets: [set(62.5, 6), set(70, 5, { isCompleted: false })] }]),
    ]);
    const bench = progress[0]!;
    expect(bench.name).toBe('Bench Press');
    expect(bench.mode).toBe('weight');
    expect(bench.points.map((p) => [p.date, p.topKg, p.topReps])).toEqual([['2026-10-05', 55, 8], ['2026-10-12', 60, 8], ['2026-10-14', 62.5, 6]]);
    expect([bench.first, bench.latest, bench.change]).toEqual([55, 62.5, 7.5]);
    expect(bench.points[1]!.e1rm).toBe(76); // 60 x (1 + 8 / 30)
    expect(bench.points[1]!.volume).toBe(60 * 8 + 60 * 7); // the warm-up is not counted
    expect(bench.bestE1rm).toBe(76);
  });

  it('tracks reps for a bodyweight exercise, and skips sets that were not done', () => {
    const progress = exerciseProgress([
      session('2026-10-05T07:00:00', [{ name: 'Push-Up', sets: [set(0, 15), set(0, 18)] }]),
      session('2026-10-12T07:00:00', [{ name: 'Push-Up', sets: [set(0, 20)] }, { name: 'Ghost', sets: [set(50, 8, { isCompleted: false })] }]),
    ]);
    expect(progress.map((e) => e.name)).toEqual(['Push-Up']);
    expect(progress[0]).toMatchObject({ mode: 'reps', unit: 'reps', first: 18, latest: 20, change: 2 });
  });

  it('puts the most trained exercise first and copes with damaged data', () => {
    const progress = exerciseProgress([
      session('2026-10-05T07:00:00', [{ name: 'Squat', sets: [set(80, 5)] }, { name: 'Row', sets: [set(50, 10)] }]),
      session('2026-10-12T07:00:00', [{ name: 'Row', sets: [set(52.5, 10)] }]),
      { date: '2026-10-13T07:00:00', exercises: '{broken' },
      { date: '2026-10-14T07:00:00' },
    ]);
    expect(progress.map((e) => e.name)).toEqual(['Row', 'Squat']);
  });
});

describe('body weight and intake', () => {
  it('shows how body weight developed', () => {
    expect(weightTrend([{ date: '2026-09-01', kg: 82 }, { date: '2026-10-01', kg: 80.5 }, { date: '2026-10-12', kg: 79.4 }])).toEqual({ first: 82, latest: 79.4, change: -2.6 });
    expect(weightTrend([{ date: '2026-09-01', kg: 82 }])).toBeNull();
    expect(weightTrend([])).toBeNull();
  });

  it('averages calories and protein over the days that were logged', () => {
    const days = [
      { id: 'a', date: '2026-10-14', calories: 2000, protein: 120 },
      { id: 'b', date: '2026-10-12', calories: 1600 },
      { id: 'c', date: '2026-10-11', calories: 1800, protein: 100 },
      { id: 'd', date: '2026-09-01', calories: 9000, protein: 900 },
    ];
    expect(intakeAverages(days, 7, NOW)).toEqual({ loggedDays: 3, calories: 1800, protein: 110 });
    expect(intakeAverages(days, 2, NOW)).toEqual({ loggedDays: 1, calories: 2000, protein: 120 });
    expect(intakeAverages([], 7, NOW)).toEqual({ loggedDays: 0, calories: null, protein: null });
  });
});

describe('the client list', () => {
  const clients = [
    { id: 'recent', lastActiveAt: 300, stats: stats({ lastWorkoutAt: 50, workouts30d: 3 }) },
    { id: 'stale', lastActiveAt: 100, stats: stats({ lastWorkoutAt: 10, workouts30d: 9 }) },
    { id: 'never', lastActiveAt: 200, stats: stats({ workouts30d: 0 }) },
    { id: 'old-app', lastActiveAt: 400 },
  ];
  const ids = (list: { id: string }[]) => list.map((c) => c.id);

  it('sorts by who needs a nudge, who is most active, or who was seen last', () => {
    expect(ids(sortClients(clients, 'recent'))).toEqual(['old-app', 'recent', 'never', 'stale']);
    expect(ids(sortClients(clients, 'attention'))).toEqual(['never', 'stale', 'recent', 'old-app']);
    expect(ids(sortClients(clients, 'workouts'))).toEqual(['stale', 'recent', 'never', 'old-app']);
  });
});
