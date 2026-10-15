import { describe, expect, it } from 'vitest';
import { blankExercise, changesToSentPlan, describePlanExercise, exercisesFromLibraryPlan, moveRow, notInRows, storedSets, type DraftExercise } from './assign';
import { blankPlanDraft, draftFromPlan, exerciseCount, planGroups, unknownExercises, validatePlanLibrary, type PlanLibraryDraft } from './planLibrary';
import type { AssignedExercise, ExerciseRow, LibraryPlan } from './types';

const row = (over: Partial<DraftExercise> = {}): DraftExercise => ({ ...blankExercise(), name: 'Bench Press', sets: '4', reps: '8', weightKg: '40', ...over });
const draft = (over: Partial<PlanLibraryDraft> = {}): PlanLibraryDraft => ({ ...blankPlanDraft(), name: ' Push Day ', exercises: [row()], ...over });

describe('validatePlanLibrary', () => {
  it('turns a filled-in form into the plan that is saved', () => {
    const result = validatePlanLibrary(draft({ description: ' Chest and triceps ', exercises: [row({ notes: ' Pause ' }), row({ name: 'Push-Up', weightKg: '' })] }));
    expect(result).toEqual({
      ok: true,
      value: {
        name: 'Push Day', description: 'Chest and triceps', level: 'Beginner', active: true,
        exercises: [
          { name: 'Bench Press', sets: 4, reps: 8, weightKg: 40, dropSets: 0, notes: 'Pause' },
          { name: 'Push-Up', sets: 4, reps: 8, weightKg: 0, dropSets: 0, notes: '' },
        ],
      },
    });
  });

  it('needs a name and at least one exercise, and ignores rows left empty', () => {
    expect(validatePlanLibrary(draft({ exercises: [row(), blankExercise()] })).ok).toBe(true);
    const result = validatePlanLibrary(draft({ name: ' ', exercises: [blankExercise()] }));
    expect(result.ok).toBe(false);
    if (!result.ok) {
      expect(result.errors.name).toBe('Give the plan a name.');
      expect(result.errors.exercises).toBe('Add at least one exercise.');
    }
  });

  it('keeps the limits the rules and the app apply', () => {
    const long = validatePlanLibrary(draft({ name: 'x'.repeat(61), description: 'y'.repeat(501), level: 'z'.repeat(41) }));
    expect(long.ok).toBe(false);
    if (!long.ok) expect(Object.keys(long.errors).sort()).toEqual(['description', 'level', 'name']);
    const many = validatePlanLibrary(draft({ exercises: Array.from({ length: 31 }, () => row()) }));
    expect(many.ok).toBe(false);
    if (!many.ok) expect(many.errors.exercises).toBe('At most 30 exercises.');
  });

  it('says what is wrong with an exercise, by the row', () => {
    const bad = row({ sets: '0' });
    const result = validatePlanLibrary(draft({ exercises: [bad, row()] }));
    expect(result.ok).toBe(false);
    if (!result.ok) expect(result.errors.rows).toEqual({ [bad.key]: 'Sets: a whole number from 1 to 20.' });
  });
});

const plan: LibraryPlan = {
  id: 'push-day', name: 'Push Day', description: 'Pressing', level: 'Intermediate', active: true,
  exercises: [
    { name: 'Bench Press', sets: 4, reps: 8, weightKg: 40, dropSets: 0, notes: '' },
    { name: 'Lateral Raise', sets: 3, reps: 15, weightKg: 8, dropSets: 1, notes: 'Slow' },
  ],
};

describe('a plan in the form', () => {
  it('opens as editable rows and comes back the same', () => {
    const opened = draftFromPlan(plan);
    expect(opened.exercises.map((e) => [e.name, e.sets, e.reps, e.weightKg, e.dropSets, e.notes])).toEqual([
      ['Bench Press', '4', '8', '40', '0', ''],
      ['Lateral Raise', '3', '15', '8', '1', 'Slow'],
    ]);
    const saved = validatePlanLibrary(opened);
    expect(saved.ok && saved.value.exercises).toEqual(plan.exercises);
    expect(exercisesFromLibraryPlan(plan)).toHaveLength(2);
  });

  it('finds its muscle groups through the exercise library', () => {
    const library = [{ name: 'bench press', muscleGroup: 'Chest' }, { name: 'Lateral Raise', muscleGroup: 'Shoulders' }, { name: 'Squat', muscleGroup: 'Legs' }];
    expect(planGroups(plan, library)).toEqual(['Chest', 'Shoulders']);
    expect(unknownExercises(plan, library.slice(0, 1))).toEqual(['Lateral Raise']);
    expect(exerciseCount(plan)).toBe('2 exercises');
    expect(exerciseCount({ exercises: [plan.exercises[0]!] })).toBe('1 exercise');
  });
});

const sent: AssignedExercise[] = [
  { name: 'Bench Press', sets: 3, reps: 10, weightKg: 60, dropSets: 0, notes: '' },
  { name: 'Push-Up', sets: 3, reps: 15, weightKg: 0, dropSets: 0, notes: '' },
];
const stored = (name: string, sets: [number, number, string?][], over: Partial<ExerciseRow> = {}): ExerciseRow => ({
  id: name, name, sets: String(sets.length), reps: String(sets[0]?.[1] ?? 0), weight: String(Math.max(...sets.map((s) => s[0]))), dropSets: 0,
  setsData: JSON.stringify(sets.map(([weight, reps, type]) => ({ weight, reps, ...(type ? { type } : {}) }))), ...over,
});
const asSent = (name: string, sets: number, reps: number, weight: number): ExerciseRow => ({ id: name, name, sets: String(sets), reps: String(reps), weight: String(weight), dropSets: 0 });

describe('what a client did to a plan sent from here', () => {
  it('says nothing about a plan left as it was sent', () => {
    expect(changesToSentPlan(sent, [asSent('Bench Press', 3, 10, 60), asSent('Push-Up', 3, 15, 0)])).toEqual([]);
    expect(changesToSentPlan(sent, [stored('Bench Press', [[60, 10], [60, 10], [60, 10]]), asSent('Push-Up', 3, 15, 0)])).toEqual([]);
  });

  it('reports a heavier set and fewer reps on the last set, set by set', () => {
    const changed = [stored('Bench Press', [[60, 10], [65, 10], [60, 8]]), asSent('Push-Up', 3, 15, 0)];
    expect(changesToSentPlan(sent, changed)).toEqual(['Bench Press: set 2: 60 → 65 kg, set 3: 10 → 8 reps']);
  });

  it('reports sets added or removed, drop sets, and exercises added or taken out', () => {
    const rows = [stored('bench press', [[60, 10], [60, 10], [60, 10], [60, 6]], { dropSets: 1 }), asSent('Pull-Up', 3, 8, 0)];
    expect(changesToSentPlan(sent, rows)).toEqual([
      'Bench Press: set 4 added (60 kg × 6), drop sets 0 → 1',
      'Removed Push-Up',
      'Added Pull-Up',
    ]);
    expect(changesToSentPlan(sent, [stored('Bench Press', [[60, 10], [60, 10]]), asSent('Push-Up', 3, 15, 0)])).toEqual(['Bench Press: set 3 removed']);
  });
});

describe('a plan exercise in words', () => {
  it('shows the usual line while every set is the same', () => {
    expect(describePlanExercise(asSent('Bench Press', 3, 10, 60))).toBe('3 × 10 @ 60 kg');
    expect(describePlanExercise(stored('Bench Press', [[60, 10], [60, 10], [60, 10]]))).toBe('3 × 10 @ 60 kg');
    expect(describePlanExercise(asSent('Push-Up', 3, 15, 0))).toBe('3 × 15, bodyweight');
  });

  it('shows set by set once the client made the sets differ', () => {
    expect(describePlanExercise(stored('Bench Press', [[60, 10], [65, 10], [60, 8]]))).toBe('60 × 10 · 65 × 10 · 60 × 8');
    expect(describePlanExercise(stored('Bench Press', [[30, 12, 'warmup'], [60, 8], [55, 8, 'drop']]))).toBe('30 × 12 (warm-up) · 60 × 8 · 55 × 8 (drop)');
  });

  it('reads stored sets safely', () => {
    expect(storedSets({ setsData: '' })).toEqual([]);
    expect(storedSets({ setsData: 'not json' })).toEqual([]);
    expect(storedSets({ setsData: '{"a":1}' })).toEqual([]);
  });
});

describe('building the list of a plan', () => {
  it('offers only the library exercises the plan does not have, whatever the spelling', () => {
    const library = [{ name: 'Squat' }, { name: 'Leg Press' }, { name: 'Calf Raise' }];
    const rows = [row({ name: ' squat ' }), row({ name: 'LEG PRESS' }), blankExercise()];
    expect(notInRows(library, rows)).toEqual([{ name: 'Calf Raise' }]);
    expect(notInRows(library, [])).toEqual(library);
  });

  it('moves an exercise up or down, and stays put at the ends', () => {
    const rows = ['A', 'B', 'C'].map((name) => row({ name }));
    const names = (list: DraftExercise[]) => list.map((r) => r.name);
    expect(names(moveRow(rows, rows[1]!.key, -1))).toEqual(['B', 'A', 'C']);
    expect(names(moveRow(rows, rows[1]!.key, 1))).toEqual(['A', 'C', 'B']);
    expect(moveRow(rows, rows[0]!.key, -1)).toBe(rows);
    expect(moveRow(rows, rows[2]!.key, 1)).toBe(rows);
    expect(moveRow(rows, 99999, 1)).toBe(rows);
  });
});
