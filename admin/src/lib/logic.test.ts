import { describe, expect, it } from 'vitest';
import fs from 'node:fs';
import path from 'node:path';
import {
  dayTotals, describeSets, durationText, eventKinds, eventLabel, eventTone, filterLibrary, groupEventsByDay, matchesUser, missingFromLibrary,
  MUSCLE_GROUPS, parseJsonField, relativeTime, sessionVolume, slugify, summarizeUsers, userStatus, validateLibraryExercise, weightSeries,
} from './logic';
import type { LibraryExercise, LibraryInput } from './types';

const NOW = Date.UTC(2026, 9, 13, 12, 0, 0);
const MIN = 60_000;
const HOUR = 60 * MIN;
const DAY = 24 * HOUR;

describe('times', () => {
  it('read the way people say them', () => {
    expect(relativeTime(NOW - 20_000, NOW)).toBe('just now');
    expect(relativeTime(NOW - 5 * MIN, NOW)).toBe('5 min ago');
    expect(relativeTime(NOW - 3 * HOUR, NOW)).toBe('3 h ago');
    expect(relativeTime(NOW - DAY - HOUR, NOW)).toBe('yesterday');
    expect(relativeTime(NOW - 4 * DAY, NOW)).toBe('4 days ago');
    expect(relativeTime(NOW - 30 * DAY, NOW)).toBe('2026-09-13');
    expect(relativeTime(0, NOW)).toBe('never');
    expect(relativeTime(undefined, NOW)).toBe('never');
  });

  it('turn workout clocks into minutes or hours', () => {
    expect(durationText('00:42:14')).toBe('42 min');
    expect(durationText('01:05:00')).toBe('1 h 05 min');
    expect(durationText('12:30')).toBe('12 min');
    expect(durationText('soon')).toBe('');
    expect(durationText(undefined)).toBe('');
  });
});

describe('users', () => {
  it('are online, active today, this week, inactive or not yet seen', () => {
    expect(userStatus(NOW - 2 * MIN, NOW)).toBe('online');
    expect(userStatus(NOW - 5 * HOUR, NOW)).toBe('today');
    expect(userStatus(NOW - 3 * DAY, NOW)).toBe('week');
    expect(userStatus(NOW - 20 * DAY, NOW)).toBe('inactive');
    expect(userStatus(undefined, NOW)).toBe('never');
  });

  it('are counted by how recently they were active', () => {
    const users = [{ lastActiveAt: NOW - MIN }, { lastActiveAt: NOW - 2 * HOUR }, { lastActiveAt: NOW - 3 * DAY }, { lastActiveAt: NOW - 40 * DAY }, {}];
    expect(summarizeUsers(users, NOW)).toEqual({ total: 5, today: 2, week: 3, inactive: 2 });
  });

  it('are searched by name, email and last action', () => {
    const user = { name: 'Aisha Khan', email: 'aisha@example.com', lastEvent: 'Finished "Push Day"' };
    expect(matchesUser(user, 'aisha')).toBe(true);
    expect(matchesUser(user, 'EXAMPLE.COM')).toBe(true);
    expect(matchesUser(user, 'push day')).toBe(true);
    expect(matchesUser(user, 'bob')).toBe(false);
    expect(matchesUser(user, '  ')).toBe(true);
  });
});

describe('activity', () => {
  it('is grouped by day with Today and Yesterday', () => {
    const events = [{ at: NOW - HOUR }, { at: NOW - 2 * HOUR }, { at: NOW - DAY }, { at: NOW - 5 * DAY }];
    const groups = groupEventsByDay(events, NOW);
    expect(groups.map((g) => [g.label === '2026-10-08' ? 'date' : g.label, g.events.length])).toEqual([['Today', 2], ['Yesterday', 1], ['date', 1]]);
  });

  it('gives unknown kinds a neutral label', () => {
    expect(eventLabel('workout_finished')).toBe('Workout');
    expect(eventLabel('something_new')).toBe('Other');
    expect(eventTone('plan_deleted')).toBe('warn');
    expect(eventTone('something_new')).toBe('neutral');
    expect(eventKinds([{ type: 'plan_created' }, { type: 'plan_deleted' }, { type: 'food_logged' }])).toEqual(['Plan', 'Food']);
  });
});

describe('what a user saved', () => {
  it('reads JSON the phone stored as text, safely', () => {
    expect(parseJsonField('[{"a":1}]', [])).toEqual([{ a: 1 }]);
    expect(parseJsonField('', [])).toEqual([]);
    expect(parseJsonField('{broken', [1])).toEqual([1]);
    expect(parseJsonField([2], [])).toEqual([2]);
    expect(parseJsonField(undefined, null)).toBeNull();
  });

  it('counts workout volume without warm-ups, and counts drop sets', () => {
    const exercises = [{ name: 'Bench', sets: [{ weight: 30, reps: 10, type: 'warmup' }, { weight: 60, reps: 8 }, { weight: 55, reps: 10, type: 'drop' }] }];
    expect(sessionVolume(exercises)).toBe(60 * 8 + 55 * 10);
    expect(sessionVolume(undefined)).toBe(0);
    expect(describeSets(exercises[0]?.sets)).toBe('W 30 × 10, 60 × 8, D 55 × 10');
    expect(describeSets([{ weight: 0, reps: 15 }])).toBe('15 reps');
    expect(describeSets([{ weight: 60, reps: 8, isCompleted: false }])).toBe('');
  });

  it('tidies weigh-ins and food for display', () => {
    expect(weightSeries([{ id: 'a', date: '2026-10-02', weight: 71 }, { id: 'b', date: 'bad', weight: 70 }, { id: 'c', date: '2026-10-01', weight: '72.5' }]))
      .toEqual([{ date: '2026-10-01', kg: 72.5 }, { date: '2026-10-02', kg: 71 }]);
    const days = dayTotals([{ id: '1', date: '2026-10-01', calories: 300 }, { id: '2', date: '2026-10-02', calories: 500 }, { id: '3', date: '2026-10-01', calories: 200 }]);
    expect(days.map((d) => [d.date, d.kcal])).toEqual([['2026-10-02', 500], ['2026-10-01', 500]]);
  });
});

describe('the library', () => {
  const good: LibraryInput = {
    name: 'Bench Press', muscleGroup: 'Chest', equipment: 'Barbell', level: 'Beginner', instructions: 'Lie down and press.', tips: 'Wrists straight.',
    defaultSets: '4', defaultReps: '8', defaultWeightKg: '40', videoUrl: '', active: true,
  };
  const check = (over: Partial<LibraryInput>) => validateLibraryExercise({ ...good, ...over });
  const errorsOf = (over: Partial<LibraryInput>) => { const r = check(over); return r.ok ? {} : r.errors; };

  it('accepts and cleans a good exercise', () => {
    const result = check({ name: '  Bench Press  ' });
    expect(result.ok).toBe(true);
    if (result.ok) {
      expect(result.value.name).toBe('Bench Press');
      expect(result.value.defaultSets).toBe(4);
      expect(result.value.defaultWeightKg).toBe(40);
      expect(result.value.active).toBe(true);
    }
  });

  it('checks what firestore.rules would refuse', () => {
    expect(errorsOf({ name: '' }).name).toBeTruthy();
    expect(errorsOf({ name: 'x'.repeat(101) }).name).toBeTruthy();
    expect(errorsOf({ muscleGroup: '' }).muscleGroup).toBeTruthy();
    expect(errorsOf({ defaultSets: 0 }).defaultSets).toBeTruthy();
    expect(errorsOf({ defaultSets: 2.5 }).defaultSets).toBeTruthy();
    expect(errorsOf({ defaultReps: 101 }).defaultReps).toBeTruthy();
    expect(errorsOf({ defaultWeightKg: -1 }).defaultWeightKg).toBeTruthy();
    expect(errorsOf({ defaultWeightKg: 'heavy' }).defaultWeightKg).toBeTruthy();
    expect(errorsOf({ instructions: 'x'.repeat(4001) }).instructions).toBeTruthy();
    expect(errorsOf({ videoUrl: 'javascript:alert(1)' }).videoUrl).toBeTruthy();
    expect(errorsOf({ videoUrl: 'http://example.com/v' }).videoUrl).toBeTruthy();
    expect(check({ videoUrl: 'https://example.com/v' }).ok).toBe(true);
  });

  it('keeps the visibility and drops an unknown level', () => {
    const hidden = check({ active: false });
    expect(hidden.ok && hidden.value.active).toBe(false);
    const odd = check({ level: 'Wizard' });
    expect(odd.ok && odd.value.level).toBe('');
  });

  it('makes ids from names', () => {
    expect(slugify('Bench Press')).toBe('bench-press');
    expect(slugify("Farmer's Walk")).toBe('farmer-s-walk');
    expect(slugify('  --Hello!!  ')).toBe('hello');
  });

  it('never overwrites what is there when importing the starter set', () => {
    const seed = [{ id: 'a' }, { id: 'b' }, { id: 'c' }, null, { id: '' }];
    expect(missingFromLibrary(seed, ['b']).map((e) => e.id)).toEqual(['a', 'c']);
  });

  it('filters and keeps the usual group order', () => {
    const base = { equipment: '', level: '', instructions: '', tips: '', defaultSets: 3, defaultReps: 10, defaultWeightKg: 0, videoUrl: '' };
    const list: LibraryExercise[] = [
      { ...base, id: 'squat', name: 'Squat', muscleGroup: 'Legs', equipment: 'Barbell', active: true },
      { ...base, id: 'bench', name: 'Bench Press', muscleGroup: 'Chest', equipment: 'Barbell', active: true },
      { ...base, id: 'push', name: 'Push-Up', muscleGroup: 'Chest', equipment: 'Bodyweight', active: false },
      { ...base, id: 'odd', name: 'Odd', muscleGroup: 'Mystery', active: true },
    ];
    const names = (f: Partial<{ query: string; group: string; status: 'all' | 'active' | 'hidden' }>) =>
      filterLibrary(list, { query: '', group: '', status: 'all', ...f }).map((e) => e.name);
    expect(names({})).toEqual(['Bench Press', 'Push-Up', 'Squat', 'Odd']);
    expect(names({ group: 'Chest' })).toEqual(['Bench Press', 'Push-Up']);
    expect(names({ query: 'body' })).toEqual(['Push-Up']);
    expect(names({ status: 'active' })).toEqual(['Bench Press', 'Squat', 'Odd']);
    expect(names({ status: 'hidden' })).toEqual(['Push-Up']);
  });

  it('has a starter file that passes the same checks as the form', () => {
    const seed = JSON.parse(fs.readFileSync(path.join(__dirname, '../../seed/library.json'), 'utf8')) as LibraryInput[] & { id: string }[];
    expect(seed.length).toBeGreaterThanOrEqual(60);
    for (const exercise of seed) {
      const result = validateLibraryExercise(exercise);
      expect(result.ok, `${exercise.name}: ${JSON.stringify(result.ok ? {} : result.errors)}`).toBe(true);
      expect(MUSCLE_GROUPS).toContain(exercise.muscleGroup);
    }
  });
});
