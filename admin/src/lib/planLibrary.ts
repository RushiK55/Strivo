// The plan library: plans an admin builds from the exercise library, which every user can browse in the app and put on a day.
// Pure functions, covered by planLibrary.test.ts. The checks match firestore.rules and the ranges the app clamps to.
import { blankExercise, checkExerciseRows, exercisesFromLibraryPlan, type DraftExercise } from './assign';
import { MUSCLE_GROUPS } from './logic';
import type { LibraryExercise, LibraryPlan, LibraryPlanFields } from './types';

/** What the plan form edits, before it is checked: the numbers of each exercise are text until validated. */
export interface PlanLibraryDraft {
  name: string;
  description: string;
  level: string;
  active: boolean;
  exercises: DraftExercise[];
}

export interface PlanLibraryErrors {
  name?: string;
  description?: string;
  level?: string;
  /** About the list as a whole (none, too many). */
  exercises?: string;
  /** One message per exercise row that has a problem, by its key. */
  rows?: Record<number, string>;
}

export type PlanLibraryCheck = { ok: true; value: LibraryPlanFields } | { ok: false; errors: PlanLibraryErrors };

export const blankPlanDraft = (): PlanLibraryDraft => ({ name: '', description: '', level: 'Beginner', active: true, exercises: [blankExercise()] });

export const draftFromPlan = (plan: LibraryPlan): PlanLibraryDraft => ({
  name: plan.name,
  description: plan.description ?? '',
  level: plan.level ?? '',
  active: plan.active,
  exercises: exercisesFromLibraryPlan(plan),
});

export function validatePlanLibrary(draft: PlanLibraryDraft): PlanLibraryCheck {
  const errors: PlanLibraryErrors = {};
  const name = draft.name.trim();
  if (!name) errors.name = 'Give the plan a name.';
  else if (name.length > 60) errors.name = 'At most 60 characters.';
  const description = draft.description.trim();
  if (description.length > 500) errors.description = 'At most 500 characters.';
  const level = draft.level.trim();
  if (level.length > 40) errors.level = 'At most 40 characters.';

  const { exercises, list, rows } = checkExerciseRows(draft.exercises);
  if (list) errors.exercises = list;
  if (rows) errors.rows = rows;

  if (Object.keys(errors).length) return { ok: false, errors };
  return { ok: true, value: { name, description, level, active: draft.active, exercises } };
}

/** The muscle groups a plan trains, found through the exercise library, in the usual order ("Chest, Shoulders, Triceps"). */
export function planGroups(plan: Pick<LibraryPlan, 'exercises'>, library: Pick<LibraryExercise, 'name' | 'muscleGroup'>[]): string[] {
  const byName = new Map(library.map((exercise) => [exercise.name.trim().toLowerCase(), exercise.muscleGroup]));
  const found = new Set<string>();
  for (const exercise of plan.exercises) {
    const group = byName.get(exercise.name.trim().toLowerCase());
    if (group) found.add(group);
  }
  return [...found].sort((a, b) => (MUSCLE_GROUPS.indexOf(a) + 1 || 99) - (MUSCLE_GROUPS.indexOf(b) + 1 || 99));
}

/** Exercises of a plan that are not in the exercise library any more (by name), so the admin can fix the plan. */
export function unknownExercises(plan: Pick<LibraryPlan, 'exercises'>, library: Pick<LibraryExercise, 'name'>[]): string[] {
  const known = new Set(library.map((exercise) => exercise.name.trim().toLowerCase()));
  return plan.exercises.map((exercise) => exercise.name).filter((name) => !known.has(name.trim().toLowerCase()));
}

/** "6 exercises" */
export const exerciseCount = (plan: Pick<LibraryPlan, 'exercises'>): string =>
  `${plan.exercises.length} ${plan.exercises.length === 1 ? 'exercise' : 'exercises'}`;
