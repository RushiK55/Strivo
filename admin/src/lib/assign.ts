// Plans a trainer sends to a client (users/{uid}/assigned). Pure functions, covered by assign.test.ts.
// The checks match firestore.rules and the ranges the app clamps to, so a plan that passes here is accepted everywhere.
import type { AssignedExercise, AssignmentFields, ExerciseRow, LibraryExercise, LibraryPlan } from './types';

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

const nameKey = (name: string): string => name.trim().toLowerCase();

/** The library exercises a plan does not have yet (by name), so a list to add from never offers what is already in the plan. */
export function notInRows<T extends { name: string }>(library: T[], rows: Pick<DraftExercise, 'name'>[]): T[] {
  const have = new Set(rows.map((row) => nameKey(row.name)).filter(Boolean));
  return library.filter((exercise) => !have.has(nameKey(exercise.name)));
}

/** The rows with the one called [key] moved [by] places (-1 up, +1 down); the same rows when it cannot move that way. */
export function moveRow<T extends { key: number }>(rows: T[], key: number, by: number): T[] {
  const from = rows.findIndex((row) => row.key === key);
  const to = from + by;
  if (from < 0 || to < 0 || to >= rows.length) return rows;
  const next = [...rows];
  const [moved] = next.splice(from, 1);
  next.splice(to, 0, moved!);
  return next;
}

/** A plan of the plan library as rows to edit, so a trainer can send it as it is or change it first. */
export const exercisesFromLibraryPlan = (plan: LibraryPlan): DraftExercise[] =>
  plan.exercises.slice(0, MAX_ASSIGNED_EXERCISES).map((e) => ({
    key: key(), name: e.name, sets: String(e.sets), reps: String(e.reps), weightKg: String(e.weightKg), dropSets: String(e.dropSets ?? 0), notes: e.notes ?? '',
  }));

const whole = (text: string, min: number, max: number): number | null => {
  const value = Number(text.trim());
  return text.trim() !== '' && Number.isInteger(value) && value >= min && value <= max ? value : null;
};

/** The exercise rows of a form, checked: those left completely empty are ignored, anything else must be right. */
export interface CheckedRows {
  exercises: AssignedExercise[];
  /** About the list as a whole (none, too many). */
  list?: string;
  /** One message per row that has a problem, by its key. */
  rows?: Record<number, string>;
}

export function checkExerciseRows(draftRows: DraftExercise[]): CheckedRows {
  const filled = draftRows.filter((row) => row.name.trim() || row.notes.trim());
  let list: string | undefined;
  if (!filled.length) list = 'Add at least one exercise.';
  else if (filled.length > MAX_ASSIGNED_EXERCISES) list = `At most ${MAX_ASSIGNED_EXERCISES} exercises.`;

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
  return { exercises, ...(list ? { list } : {}), ...(Object.keys(rows).length ? { rows } : {}) };
}

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

  const { exercises, list, rows } = checkExerciseRows(draft.exercises);
  if (list) errors.exercises = list;
  if (rows) errors.rows = rows;

  if (Object.keys(errors).length) return { ok: false, errors };
  return { ok: true, value: { planName, day: draft.day, note, sentByName, exercises } };
}

/** "4 × 8 + 1 drop @ 60 kg", "3 × 15, bodyweight" */
export const describeAssigned = (e: AssignedExercise): string =>
  `${e.sets} × ${e.reps}${e.dropSets > 0 ? ` + ${e.dropSets} drop` : ''}${e.weightKg > 0 ? ` @ ${e.weightKg} kg` : ', bodyweight'}`;

// ---- What a client did to a plan ----

/** One set of a plan as the app stores it (setsData, JSON text). */
interface StoredSet { weight?: number; reps?: number; type?: string }

/** The sets of a plan exercise from the client's phone: weight and reps of each, whether or not they are all the same. Empty when it has none stored. */
export function storedSets(row: Pick<ExerciseRow, 'setsData'>): { weight: number; reps: number; type: string }[] {
  if (!row.setsData) return [];
  try {
    const parsed: unknown = JSON.parse(row.setsData);
    if (!Array.isArray(parsed)) return [];
    return (parsed as StoredSet[]).map((s) => ({ weight: Number(s.weight) || 0, reps: Number(s.reps) || 0, type: s.type ?? 'normal' }));
  } catch {
    return [];
  }
}

const kg = (value: number): string => (Number.isInteger(value) ? String(value) : value.toFixed(1));

/**
 * A plan exercise in words, set by set when the client has made the sets differ ("60 × 10 · 65 × 10 · 60 × 8"), otherwise the usual
 * "3 × 10 @ 60 kg". Warm-ups and drop sets are marked.
 */
export function describePlanExercise(row: ExerciseRow): string {
  const sets = storedSets(row);
  const uniform = sets.every((s) => s.type === sets[0]?.type && s.weight === sets[0]?.weight && s.reps === sets[0]?.reps);
  const summary = `${row.sets} × ${row.reps}${Number(row.dropSets) > 0 ? ` + ${row.dropSets} drop` : ''}${Number(row.weight) > 0 ? ` @ ${row.weight} kg` : ', bodyweight'}`;
  if (sets.length < 2 || uniform) return summary;
  const mark: Record<string, string> = { warmup: ' (warm-up)', drop: ' (drop)', failure: ' (to failure)' };
  return sets.map((s) => `${s.weight > 0 ? kg(s.weight) : 'BW'} × ${s.reps}${mark[s.type] ?? ''}`).join(' · ');
}

/** The sets a plan exercise sent from here starts with: the working sets, then the drop sets are only counted. */
function sentSets(e: AssignedExercise): { weight: number; reps: number }[] {
  return Array.from({ length: e.sets }, () => ({ weight: e.weightKg, reps: e.reps }));
}

/** The working sets of a client's exercise: stored set by set, or described by its sets, reps and weight. */
function workingSets(row: ExerciseRow): { weight: number; reps: number }[] {
  const sets = storedSets(row).filter((s) => s.type === 'normal' || s.type === 'failure');
  if (sets.length) return sets.map(({ weight, reps }) => ({ weight, reps }));
  return Array.from({ length: Math.max(0, Number(row.sets) || 0) }, () => ({ weight: Number(row.weight) || 0, reps: Number(row.reps) || 0 }));
}

const sameName = (a: string, b: string): boolean => a.trim().toLowerCase() === b.trim().toLowerCase();

/**
 * What the client changed in a plan sent from here, in plain words: an exercise added or taken out, a set made heavier or lighter, a
 * set with more or fewer reps, a set added or removed, drop sets. Empty when the plan is as it was sent. [rows] are the exercises of
 * the plan in the client's app, in order.
 */
export function changesToSentPlan(sent: AssignedExercise[], rows: ExerciseRow[]): string[] {
  const changes: string[] = [];
  const used = new Set<ExerciseRow>();
  for (const e of sent) {
    const row = rows.find((r) => !used.has(r) && sameName(String(r.name ?? ''), e.name));
    if (!row) { changes.push(`Removed ${e.name}`); continue; }
    used.add(row);
    const was = sentSets(e);
    const now = workingSets(row);
    const parts: string[] = [];
    for (let i = 0; i < Math.max(was.length, now.length); i++) {
      const a = was[i];
      const b = now[i];
      if (!b) parts.push(`set ${i + 1} removed`);
      else if (!a) parts.push(`set ${i + 1} added (${kg(b.weight)} kg × ${b.reps})`);
      else {
        if (a.weight !== b.weight) parts.push(`set ${i + 1}: ${kg(a.weight)} → ${kg(b.weight)} kg`);
        if (a.reps !== b.reps) parts.push(`set ${i + 1}: ${a.reps} → ${b.reps} reps`);
      }
    }
    const drops = Number(row.dropSets) || 0;
    if (drops !== (e.dropSets ?? 0)) parts.push(`drop sets ${e.dropSets ?? 0} → ${drops}`);
    if (parts.length) changes.push(`${e.name}: ${parts.join(', ')}`);
  }
  for (const row of rows) if (!used.has(row)) changes.push(`Added ${row.name}`);
  return changes;
}
