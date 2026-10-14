import { describe, expect, it } from 'vitest';
import { blankExercise, describeAssigned, exerciseFromLibrary, exerciseFromPlan, validateAssignment, type AssignmentDraft, type DraftExercise } from './assign';
import type { LibraryExercise } from './types';

const row = (over: Partial<DraftExercise> = {}): DraftExercise => ({ ...blankExercise(), name: 'Bench Press', sets: '4', reps: '8', weightKg: '60', ...over });
const draft = (over: Partial<AssignmentDraft> = {}): AssignmentDraft => ({
  planName: ' Push Day ', day: 'Monday', note: '', sentByName: 'Coach', exercises: [row()], ...over,
});

describe('validateAssignment', () => {
  it('turns a filled-in form into the plan that is sent', () => {
    const result = validateAssignment(draft({ exercises: [row({ notes: ' Slow down ' }), row({ name: 'Push-up', weightKg: '', dropSets: '' })] }));
    expect(result).toEqual({
      ok: true,
      value: {
        planName: 'Push Day', day: 'Monday', note: '', sentByName: 'Coach',
        exercises: [
          { name: 'Bench Press', sets: 4, reps: 8, weightKg: 60, dropSets: 0, notes: 'Slow down' },
          { name: 'Push-up', sets: 4, reps: 8, weightKg: 0, dropSets: 0, notes: '' },
        ],
      },
    });
  });

  it('ignores rows left empty, but needs at least one exercise', () => {
    expect(validateAssignment(draft({ exercises: [row(), blankExercise()] })).ok).toBe(true);
    const result = validateAssignment(draft({ exercises: [blankExercise()] }));
    expect(result.ok).toBe(false);
    if (!result.ok) expect(result.errors.exercises).toBe('Add at least one exercise.');
  });

  it('says what is wrong with each row, by the row', () => {
    const bad = [row({ sets: '0' }), row({ reps: '8.5' }), row({ weightKg: '-1' }), row({ dropSets: '9' }), row({ name: '', notes: 'only a note' })];
    const result = validateAssignment(draft({ exercises: bad }));
    expect(result.ok).toBe(false);
    if (result.ok) return;
    expect(Object.values(result.errors.rows ?? {})).toEqual([
      'Sets: a whole number from 1 to 20.',
      'Reps: a whole number from 1 to 100.',
      'Weight: 0 to 1000 kg (0 for bodyweight).',
      'Drop sets: 0 to 5.',
      'Name the exercise.',
    ]);
  });

  it('checks the plan name and day like the rules do', () => {
    const result = validateAssignment(draft({ planName: '  ', day: 'Someday' }));
    expect(result.ok).toBe(false);
    if (!result.ok) {
      expect(result.errors.planName).toBe('Give the plan a name.');
      expect(result.errors.day).toBe('Pick a day.');
    }
    expect(validateAssignment(draft({ day: 'Prebuilt' })).ok).toBe(true);
    expect(validateAssignment(draft({ planName: 'x'.repeat(61) })).ok).toBe(false);
    expect(validateAssignment(draft({ exercises: Array.from({ length: 31 }, () => row()) })).ok).toBe(false);
  });
});

describe('starting rows', () => {
  it('copies a library exercise with its suggested values', () => {
    const library = { name: 'Squat', defaultSets: 5, defaultReps: 5, defaultWeightKg: 80, tips: 'Brace' } as LibraryExercise;
    expect(exerciseFromLibrary(library)).toMatchObject({ name: 'Squat', sets: '5', reps: '5', weightKg: '80', dropSets: '0', notes: 'Brace' });
  });

  it("copies one of the client's own exercises", () => {
    expect(exerciseFromPlan({ id: '1', name: 'Row', sets: '3', reps: '12', weight: '40.0', dropSets: 1 })).toMatchObject({ name: 'Row', sets: '3', reps: '12', weightKg: '40', dropSets: '1' });
  });

  it('gives every row its own key', () => {
    expect(blankExercise().key).not.toBe(blankExercise().key);
  });
});

it('describes an exercise in one line', () => {
  expect(describeAssigned({ name: 'Bench', sets: 4, reps: 8, weightKg: 60, dropSets: 1, notes: '' })).toBe('4 × 8 + 1 drop @ 60 kg');
  expect(describeAssigned({ name: 'Push-up', sets: 3, reps: 15, weightKg: 0, dropSets: 0, notes: '' })).toBe('3 × 15, bodyweight');
});
