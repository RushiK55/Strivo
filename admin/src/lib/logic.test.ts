import { describe, expect, it } from 'vitest';
import fs from 'node:fs';
import path from 'node:path';
import {
  actionsPerDay, cleanMuscles, describeSets, durationText, eventKinds, eventLabel, eventTone, filterLibrary, groupEventsByDay, initials, kindBreakdown, matchesUser, missingFromLibrary, safeSummary,
  MAX_ABOUT, MAX_SUBTITLE, MUSCLE_GROUPS, musclesToFill, parseJsonField, relativeTime, sessionVolume, slugify, statusCounts, summarizeUsers, userStatus, validateLibraryExercise, validateMuscleInfo,
  weightSeries, withMuscleRole,
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

  it('are searched by name and email', () => {
    const user = { name: 'Aisha Khan', email: 'aisha@example.com', lastEvent: 'Finished "Push Day"' };
    expect(matchesUser(user, 'aisha')).toBe(true);
    expect(matchesUser(user, 'EXAMPLE.COM')).toBe(true);
    expect(matchesUser(user, 'push day')).toBe(false); // the last action is not searched
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

  it('tidies weigh-ins for display', () => {
    expect(weightSeries([{ id: 'a', date: '2026-10-02', weight: 71 }, { id: 'b', date: 'bad', weight: 70 }, { id: 'c', date: '2026-10-01', weight: '72.5' }]))
      .toEqual([{ date: '2026-10-01', kg: 72.5 }, { date: '2026-10-02', kg: 71 }]);
  });
});

describe('the library', () => {
  const good: LibraryInput = {
    name: 'Bench Press', muscleGroup: 'Chest', equipment: 'Barbell', level: 'Beginner', instructions: 'Lie down and press.', tips: 'Wrists straight.',
    defaultSets: '4', defaultReps: '8', defaultWeightKg: '40', videoUrl: '', active: true,
  };
  const check = (over: Partial<LibraryInput>) => validateLibraryExercise({ ...good, ...over });
  const errorsOf = (over: Partial<LibraryInput>) => { const r = check(over); return r.ok ? {} : r.errors; };

  it('keeps the muscles an exercise trains, cleaned', () => {
    const result = check({ primaryMuscles: ['lats', 'lats', 'Bad Id', 'biceps'], secondaryMuscles: ['biceps', 'mid-back', 7 as unknown as string] });
    expect(result.ok).toBe(true);
    if (result.ok) {
      expect(result.value.primaryMuscles).toEqual(['lats', 'biceps']);
      // biceps is a main muscle, so it is not repeated as a helper
      expect(result.value.secondaryMuscles).toEqual(['mid-back']);
    }
    const none = check({});
    if (none.ok) expect(none.value.primaryMuscles).toEqual([]);
  });

  it('refuses more muscles than the rules allow', () => {
    const many = ['a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i'];
    expect(errorsOf({ primaryMuscles: many }).primaryMuscles).toMatch(/at most 8/i);
    expect(errorsOf({ secondaryMuscles: many }).secondaryMuscles).toMatch(/at most 8/i);
  });

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

describe('filling in muscles on exercises that were added before they existed', () => {
  const seed = [
    { id: 'pull-up', primaryMuscles: ['lats'], secondaryMuscles: ['biceps', 'lats'] },
    { id: 'curl', primaryMuscles: ['biceps'], secondaryMuscles: [] },
    { id: 'no-muscles', primaryMuscles: [] },
    { id: 'not-in-library', primaryMuscles: ['chest'] },
  ];

  it('writes only to exercises that have none, from what the starter library knows', () => {
    const existing = [{ id: 'pull-up' }, { id: 'curl', primaryMuscles: ['biceps'] }, { id: 'no-muscles' }, { id: 'mine', primaryMuscles: undefined }];
    expect(musclesToFill(seed, existing)).toEqual([{ id: 'pull-up', primaryMuscles: ['lats'], secondaryMuscles: ['biceps'] }]);
  });

  it('does nothing when every exercise already has its muscles', () => {
    expect(musclesToFill(seed, [{ id: 'pull-up', primaryMuscles: ['lats'] }, { id: 'curl', primaryMuscles: ['biceps'] }])).toEqual([]);
  });

  it('reads a list of muscles safely', () => {
    expect(cleanMuscles(undefined)).toEqual([]);
    expect(cleanMuscles('lats')).toEqual([]);
    expect(cleanMuscles(['lats', 'lats', 'delt-front', 'Delt Front', '', null])).toEqual(['lats', 'delt-front']);
  });
});

describe('chart and avatar helpers', () => {
  it('counts actions per day, with zero for quiet days', () => {
    const events = [{ at: NOW - HOUR }, { at: NOW - 2 * HOUR }, { at: NOW - 2 * DAY }];
    expect(actionsPerDay(events, 4, NOW)).toEqual([
      { date: '2026-10-10', count: 0 },
      { date: '2026-10-11', count: 1 },
      { date: '2026-10-12', count: 0 },
      { date: '2026-10-13', count: 2 },
    ]);
  });

  it('ranks what users do most', () => {
    const events = [{ type: 'plan_created' }, { type: 'plan_deleted' }, { type: 'workout_finished' }, { type: 'weird' }];
    expect(kindBreakdown(events)).toEqual([
      { label: 'Plan', count: 2, share: 0.5 },
      { label: 'Other', count: 1, share: 0.25 },
      { label: 'Workout', count: 1, share: 0.25 },
    ]);
    expect(kindBreakdown([])).toEqual([]);
  });

  it('counts users per status', () => {
    const users = [{ lastActiveAt: NOW - MIN }, { lastActiveAt: NOW - 3 * HOUR }, { lastActiveAt: NOW - 20 * DAY }, {}];
    expect(statusCounts(users, NOW)).toEqual({ online: 1, today: 1, week: 0, inactive: 1, never: 1 });
  });

  it('makes avatar letters', () => {
    expect(initials('Aisha Khan', 'x@y.z')).toBe('AK');
    expect(initials('Madonna', undefined)).toBe('MA');
    expect(initials('  ', 'bob@example.com')).toBe('BO');
    expect(initials(undefined, undefined)).toBe('?');
  });
});

describe('what a trainer may read of the activity lines', () => {
  it('never shows what was eaten, even in lines an older version of the app wrote', () => {
    expect(safeSummary('food_logged', 'Logged "Dal and rice" (560 kcal) as dinner')).toBe('Logged a meal (560 kcal)');
    expect(safeSummary('food_logged', 'Logged "<img src=x onerror=1>Dal" (90 kcal) as snack')).toBe('Logged a meal (90 kcal)');
    expect(safeSummary('food_logged', 'something unexpected with Dal')).toBe('Logged a meal');
  });

  it('leaves the lines without a food name alone', () => {
    expect(safeSummary('food_logged', 'Logged dinner (560 kcal, 32 g protein)')).toBe('Logged dinner (560 kcal, 32 g protein)');
    expect(safeSummary('workout_finished', 'Finished "Push Day": 2 exercises')).toBe('Finished "Push Day": 2 exercises');
  });
});

describe('what an admin writes about a muscle', () => {
  const builtIn = 'The broad wing-shaped muscle of the back.';

  it('keeps a subtitle and a description of their own, tidied', () => {
    const check = validateMuscleInfo({ subtitle: '  Back   wings ', about: ' The big muscle at the sides of the back. ' }, builtIn);
    expect(check).toEqual({ ok: true, value: { subtitle: 'Back wings', about: 'The big muscle at the sides of the back.' }, empty: false });
  });

  it('does not count the built-in text as their own, so a later improvement to it still shows', () => {
    const check = validateMuscleInfo({ subtitle: 'Wings', about: ` ${builtIn} ` }, builtIn);
    expect(check).toEqual({ ok: true, value: { subtitle: 'Wings', about: '' }, empty: false });
  });

  it('says nothing is overridden when both are empty or the description is the built-in one', () => {
    expect(validateMuscleInfo({ subtitle: '', about: '' }, builtIn)).toMatchObject({ ok: true, empty: true });
    expect(validateMuscleInfo({ subtitle: '   ', about: builtIn }, builtIn)).toMatchObject({ ok: true, empty: true });
  });

  it('refuses text longer than the rules allow, and says by how much', () => {
    const long = validateMuscleInfo({ subtitle: 'x'.repeat(MAX_SUBTITLE + 1), about: 'y'.repeat(MAX_ABOUT + 5) }, builtIn);
    expect(long.ok).toBe(false);
    if (!long.ok) {
      expect(long.errors.subtitle).toContain(String(MAX_SUBTITLE + 1));
      expect(long.errors.about).toContain(String(MAX_ABOUT + 5));
    }
    expect(validateMuscleInfo({ subtitle: 'x'.repeat(MAX_SUBTITLE), about: 'y'.repeat(MAX_ABOUT) }, builtIn).ok).toBe(true);
  });
});

describe('making a muscle a main one, a helper or neither for an exercise', () => {
  const exercise = { primaryMuscles: ['chest', 'triceps'], secondaryMuscles: ['delt-front'] };

  it('adds it as a main muscle or a helper, at the end', () => {
    expect(withMuscleRole(exercise, 'lats', 'primary')).toEqual({ ok: true, primaryMuscles: ['chest', 'triceps', 'lats'], secondaryMuscles: ['delt-front'] });
    expect(withMuscleRole(exercise, 'lats', 'secondary')).toEqual({ ok: true, primaryMuscles: ['chest', 'triceps'], secondaryMuscles: ['delt-front', 'lats'] });
  });

  it('moves it from one list to the other, never in both', () => {
    expect(withMuscleRole(exercise, 'triceps', 'secondary')).toEqual({ ok: true, primaryMuscles: ['chest'], secondaryMuscles: ['delt-front', 'triceps'] });
    expect(withMuscleRole(exercise, 'delt-front', 'primary')).toEqual({ ok: true, primaryMuscles: ['chest', 'triceps', 'delt-front'], secondaryMuscles: [] });
  });

  it('takes it out of both when the role is none, and leaves an exercise that did not have it as it is', () => {
    expect(withMuscleRole(exercise, 'chest', null)).toEqual({ ok: true, primaryMuscles: ['triceps'], secondaryMuscles: ['delt-front'] });
    expect(withMuscleRole(exercise, 'lats', null)).toEqual({ ok: true, primaryMuscles: ['chest', 'triceps'], secondaryMuscles: ['delt-front'] });
  });

  it('works for an exercise with no muscles set, and tolerates rubbish in the lists', () => {
    expect(withMuscleRole({}, 'lats', 'primary')).toEqual({ ok: true, primaryMuscles: ['lats'], secondaryMuscles: [] });
    expect(withMuscleRole({ primaryMuscles: ['chest', 'chest', 'Not Valid', 5 as unknown as string] }, 'abs', 'primary')).toEqual({ ok: true, primaryMuscles: ['chest', 'abs'], secondaryMuscles: [] });
  });

  it('refuses a ninth main or helping muscle', () => {
    const full = { primaryMuscles: ['a', 'b', 'c', 'd', 'e', 'f', 'g', 'h'], secondaryMuscles: ['i', 'j', 'k', 'l', 'm', 'n', 'o', 'p'] };
    expect(withMuscleRole(full, 'lats', 'primary')).toEqual({ ok: false, error: 'An exercise can have at most 8 main muscles.' });
    expect(withMuscleRole(full, 'lats', 'secondary')).toEqual({ ok: false, error: 'An exercise can have at most 8 helping muscles.' });
    expect(withMuscleRole(full, 'a', 'secondary')).toMatchObject({ ok: false }); // moving one over would make nine helpers
    expect(withMuscleRole(full, 'a', null)).toMatchObject({ ok: true });
  });
});
