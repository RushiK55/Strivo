// Plans a trainer sends to a client (users/{uid}/assigned). Pure functions, covered by assign.test.ts.
// The checks match firestore.rules and the ranges the app clamps to, so a plan that passes here is accepted everywhere.
import type { AssignedExercise, AssignmentFields, ExerciseRow, LibraryExercise } from './types';

/** Where a plan can go: a weekday, or the client's library of plans ('Prebuilt' in the app). */
export const ASSIGN_DAYS: { value: string; label: string }[] = [
  ...['Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday', 'Sunday'].map((day) => ({ value: day, label: day })),
  { value: 'Prebuilt', label: 'Plan library (no day)' },
];

export const dayLabel = (day: string): string => ASSIGN_DAYS.find((d) => d.value === day)?.label ?? day;

export const MAX_ASSIGNED_EXERCISES = 30;
const MAX_DROP_SETS = 5;

/** One exercise row of the form: numbers are text until checked. */
export interface DraftExercise {
  key: number;
  name: string;
  sets: string;
  reps: string;
  weightKg: string;
  dropSets: string;
  notes: string;
}

export interface AssignmentDraft {
  planName: string;
  day: string;
  note: string;
  sentByName: string;
  exercises: DraftExercise[];
}

export interface AssignmentErrors {
  planName?: string;
  day?: string;
  sentByName?: string;
  note?: string;
  /** About the list as a whole (none, too many). */
  exercises?: string;
  /** One message per exercise row that has a problem, by its key. */
  rows?: Record<number, string>;
}

export type AssignmentCheck = { ok: true; value: AssignmentFields } | { ok: false; errors: AssignmentErrors };

let nextKey = 1;
const key = () => nextKey++;

export const blankExercise = (): DraftExercise => ({ key: key(), name: '', sets: '3', reps: '10', weightKg: '0', dropSets: '0', notes: '' });

/** A library exercise as a row, with its suggested sets, reps and weight. */
export const exerciseFromLibrary = (exercise: LibraryExercise): DraftExercise => ({
  key: key(),
  name: exercise.name,
  sets: String(exercise.defaultSets),
  reps: String(exercise.defaultReps),
  weightKg: String(exercise.defaultWeightKg),
  dropSets: '0',
  notes: exercise.tips ?? '',
});

/** One of the client's own plan exercises as a row, so a trainer can start from what the client already does. */
export const exerciseFromPlan = (row: ExerciseRow): DraftExercise => ({
  key: key(),
  name: row.name ?? '',
  sets: String(row.sets ?? '3'),
  reps: String(row.reps ?? '10'),
  weightKg: String(Number(row.weight) || 0),
  dropSets: String(row.dropSets ?? 0),
  notes: row.notes ?? '',
});

const whole = (text: string, min: number, max: number): number | null => {
  const value = Number(text.trim());
  return text.trim() !== '' && Number.isInteger(value) && value >= min && value <= max ? value : null;
};

export function validateAssignment(draft: AssignmentDraft): AssignmentCheck {
  const errors: AssignmentErrors = {};
  const planName = draft.planName.trim();
  if (!planName) errors.planName = 'Give the plan a name.';
  else if (planName.length > 60) errors.planName = 'At most 60 characters.';
  if (!ASSIGN_DAYS.some((d) => d.value === draft.day)) errors.day = 'Pick a day.';
  const note = draft.note.trim();
  if (note.length > 500) errors.note = 'At most 500 characters.';
  const sentByName = draft.sentByName.trim();
  if (sentByName.length > 100) errors.sentByName = 'At most 100 characters.';

  // Rows left completely empty are ignored; anything else must be right.
  const filled = draft.exercises.filter((row) => row.name.trim() || row.notes.trim());
  if (!filled.length) errors.exercises = 'Add at least one exercise.';
  else if (filled.length > MAX_ASSIGNED_EXERCISES) errors.exercises = `At most ${MAX_ASSIGNED_EXERCISES} exercises.`;

  const rows: Record<number, string> = {};
  const exercises: AssignedExercise[] = [];
  for (const row of filled) {
    const name = row.name.trim();
    const sets = whole(row.sets, 1, 20);
    const reps = whole(row.reps, 1, 100);
    const weightText = row.weightKg.trim() === '' ? '0' : row.weightKg.trim();
    const weightKg = Number(weightText);
    const dropSets = row.dropSets.trim() === '' ? 0 : whole(row.dropSets, 0, MAX_DROP_SETS);
    const notes = row.notes.trim();
    const problem =
      !name ? 'Name the exercise.'
        : name.length > 80 ? 'The name is at most 80 characters.'
          : sets === null ? 'Sets: a whole number from 1 to 20.'
            : reps === null ? 'Reps: a whole number from 1 to 100.'
              : !Number.isFinite(weightKg) || weightKg < 0 || weightKg > 1000 ? 'Weight: 0 to 1000 kg (0 for bodyweight).'
                : dropSets === null ? `Drop sets: 0 to ${MAX_DROP_SETS}.`
                  : notes.length > 500 ? 'Notes are at most 500 characters.'
                    : null;
    if (problem) rows[row.key] = problem;
    else exercises.push({ name, sets: sets!, reps: reps!, weightKg, dropSets: dropSets!, notes });
  }
  if (Object.keys(rows).length) errors.rows = rows;

  if (Object.keys(errors).length) return { ok: false, errors };
  return { ok: true, value: { planName, day: draft.day, note, sentByName, exercises } };
}

/** "4 × 8 + 1 drop @ 60 kg", "3 × 15, bodyweight" */
export const describeAssigned = (e: AssignedExercise): string =>
  `${e.sets} × ${e.reps}${e.dropSets > 0 ? ` + ${e.dropSets} drop` : ''}${e.weightKg > 0 ? ` @ ${e.weightKg} kg` : ', bodyweight'}`;
