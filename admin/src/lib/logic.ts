// Pure functions of the dashboard: no React, no Firebase. Covered by logic.test.ts.
import type {
  ActivityEvent, FoodRow, LibraryExercise, LibraryFields, LibraryInput, SessionExercise, SessionSet, Tone, UserStatus, UserSummary, WeightRow,
} from './types';

/** How each kind of activity the app records is shown. Unknown kinds still show, with a neutral label. */
export const EVENT_TYPES: Record<string, { label: string; tone: Tone }> = {
  workout_finished: { label: 'Workout', tone: 'good' },
  plan_created: { label: 'Plan', tone: 'info' },
  plan_copied: { label: 'Plan', tone: 'info' },
  plan_deleted: { label: 'Plan', tone: 'warn' },
  exercise_added: { label: 'Exercise', tone: 'info' },
  exercise_edited: { label: 'Exercise', tone: 'neutral' },
  exercise_removed: { label: 'Exercise', tone: 'warn' },
  food_logged: { label: 'Food', tone: 'neutral' },
  activity_added: { label: 'Activity', tone: 'neutral' },
  weight_logged: { label: 'Weight', tone: 'neutral' },
  profile_updated: { label: 'Profile', tone: 'neutral' },
};

export const eventLabel = (type: string): string => EVENT_TYPES[type]?.label ?? 'Other';
export const eventTone = (type: string): Tone => EVENT_TYPES[type]?.tone ?? 'neutral';

const MINUTE = 60_000;
const HOUR = 60 * MINUTE;
const DAY = 24 * HOUR;

/** "just now", "5 min ago", "3 h ago", "yesterday", "4 days ago", then the date. */
export function relativeTime(ms: number | undefined, now: number = Date.now()): string {
  if (ms === undefined || !Number.isFinite(ms) || ms <= 0) return 'never';
  const diff = now - ms;
  if (diff < MINUTE) return 'just now';
  if (diff < HOUR) return `${Math.floor(diff / MINUTE)} min ago`;
  if (diff < DAY) return `${Math.floor(diff / HOUR)} h ago`;
  if (diff < 7 * DAY) {
    const days = Math.floor(diff / DAY);
    return days === 1 ? 'yesterday' : `${days} days ago`;
  }
  return new Date(ms).toISOString().slice(0, 10);
}

/** online (last 10 minutes) / today / this week / inactive / never. */
export function userStatus(lastActiveAt: number | undefined, now: number = Date.now()): UserStatus {
  if (lastActiveAt === undefined || !Number.isFinite(lastActiveAt) || lastActiveAt <= 0) return 'never';
  const diff = now - lastActiveAt;
  if (diff < 10 * MINUTE) return 'online';
  if (diff < DAY) return 'today';
  if (diff < 7 * DAY) return 'week';
  return 'inactive';
}

export const STATUS_LABEL: Record<UserStatus, string> = {
  online: 'Online now', today: 'Active today', week: 'This week', inactive: 'Inactive', never: 'Not yet',
};
export const STATUS_TONE: Record<UserStatus, Tone> = { online: 'good', today: 'good', week: 'info', inactive: 'warn', never: 'neutral' };

/** Counts for the users page: how many were active in the last day and week, and how many are not. */
export function summarizeUsers(users: Pick<UserSummary, 'lastActiveAt'>[], now: number = Date.now()) {
  const summary = { total: users.length, today: 0, week: 0, inactive: 0 };
  for (const user of users) {
    const status = userStatus(user.lastActiveAt, now);
    if (status === 'online' || status === 'today') summary.today += 1;
    if (status === 'online' || status === 'today' || status === 'week') summary.week += 1;
    if (status === 'inactive' || status === 'never') summary.inactive += 1;
  }
  return summary;
}

/** The users matching what was typed, by name, email or last action, ignoring case. */
export function matchesUser(user: Pick<UserSummary, 'name' | 'email' | 'lastEvent'>, query: string): boolean {
  const text = query.trim().toLowerCase();
  if (!text) return true;
  return [user.name, user.email, user.lastEvent].some((field) => typeof field === 'string' && field.toLowerCase().includes(text));
}

const dayKey = (ms: number): string => {
  const d = new Date(ms);
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
};

export interface EventGroup<T extends { at: number }> { key: string; label: string; events: T[] }

/** Events (newest first) grouped by day, with "Today" and "Yesterday" for the last two. */
export function groupEventsByDay<T extends { at: number }>(events: T[], now: number = Date.now()): EventGroup<T>[] {
  const today = dayKey(now);
  const yesterday = dayKey(now - DAY);
  const groups: EventGroup<T>[] = [];
  for (const event of events) {
    const key = dayKey(event.at);
    let group = groups[groups.length - 1];
    if (!group || group.key !== key) {
      group = { key, label: key === today ? 'Today' : key === yesterday ? 'Yesterday' : key, events: [] };
      groups.push(group);
    }
    group.events.push(event);
  }
  return groups;
}

/** The kinds of activity present in a list, for the filter chips ("Workout", "Plan", ...). */
export const eventKinds = (events: Pick<ActivityEvent, 'type'>[]): string[] => [...new Set(events.map((event) => eventLabel(event.type)))];

/** Reads a JSON text field the phone stored as a string; the fallback when it is empty or damaged. */
export function parseJsonField<T>(value: unknown, fallback: T): T {
  if (Array.isArray(value) || (value !== null && typeof value === 'object')) return value as T;
  if (typeof value !== 'string' || value.trim() === '') return fallback;
  try {
    return JSON.parse(value) as T;
  } catch {
    return fallback;
  }
}

/** The sets that count as work (everything except warm-ups), as the app counts them. */
export const workSets = (sets: SessionSet[] | undefined): SessionSet[] => (Array.isArray(sets) ? sets : []).filter((set) => set && set.type !== 'warmup');

/** Total kilograms lifted in a workout (weight times reps of the work sets). */
export function sessionVolume(exercises: SessionExercise[] | undefined): number {
  return (Array.isArray(exercises) ? exercises : []).reduce(
    (total, exercise) => total + workSets(exercise.sets).reduce((sum, set) => sum + (Number(set.weight) || 0) * (Number(set.reps) || 0), 0),
    0,
  );
}

const kg = (value: number): string => (Number.isInteger(value) ? String(value) : value.toFixed(1));

/** "60 × 8, 60 × 8, D 55 × 10" for the sets of one exercise; warm-ups are marked W, drop sets D, failure F. */
export function describeSets(sets: SessionSet[] | undefined): string {
  const badge: Record<string, string> = { warmup: 'W ', drop: 'D ', failure: 'F ' };
  return (Array.isArray(sets) ? sets : [])
    .filter((set) => set && set.isCompleted !== false)
    .map((set) => {
      const weight = Number(set.weight) || 0;
      const reps = Number(set.reps) || 0;
      return `${badge[set.type ?? ''] ?? ''}${weight > 0 ? `${kg(weight)} × ${reps}` : `${reps} reps`}`;
    })
    .join(', ');
}

/** "00:42:14" -> "42 min"; "" when it is not readable. */
export function durationText(clock: string | undefined): string {
  const parts = String(clock ?? '').split(':').map((p) => Number.parseInt(p, 10));
  if (parts.some((p) => Number.isNaN(p))) return '';
  const [a = 0, b = 0, c = 0] = parts;
  const seconds = parts.length === 3 ? a * 3600 + b * 60 + c : parts.length === 2 ? a * 60 + b : Number.NaN;
  if (Number.isNaN(seconds)) return '';
  const minutes = Math.floor(seconds / 60);
  return minutes >= 60 ? `${Math.floor(minutes / 60)} h ${String(minutes % 60).padStart(2, '0')} min` : `${minutes} min`;
}

/** Weigh-ins oldest first, with bad rows dropped. */
export function weightSeries(weights: WeightRow[]): { date: string; kg: number }[] {
  return weights
    .map((w) => ({ date: String(w.date ?? ''), kg: Number(w.weight) }))
    .filter((w) => /^\d{4}-\d{2}-\d{2}$/.test(w.date) && Number.isFinite(w.kg))
    .sort((a, b) => (a.date < b.date ? -1 : 1));
}

/** Calories per day, newest first. */
export function dayTotals(foods: FoodRow[]): { date: string; kcal: number; items: FoodRow[] }[] {
  const byDay = new Map<string, { date: string; kcal: number; items: FoodRow[] }>();
  for (const food of foods) {
    const date = String(food.date ?? '');
    const entry = byDay.get(date) ?? { date, kcal: 0, items: [] };
    entry.kcal += Number(food.calories) || 0;
    entry.items.push(food);
    byDay.set(date, entry);
  }
  return [...byDay.values()].sort((a, b) => (a.date < b.date ? 1 : -1));
}

// ---- Library ----

export const MUSCLE_GROUPS = ['Chest', 'Back', 'Shoulders', 'Biceps', 'Triceps', 'Legs', 'Glutes', 'Core', 'Cardio', 'Full body'];
export const LEVELS = ['Beginner', 'Intermediate', 'Advanced'];

/** A document id from a name: "Farmer's Walk" -> "farmer-s-walk". */
export function slugify(name: string): string {
  return String(name).toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-+|-+$/g, '').slice(0, 60);
}

export type LibraryErrors = Partial<Record<keyof LibraryInput, string>>;
export type LibraryCheck = { ok: true; value: LibraryFields } | { ok: false; errors: LibraryErrors };

/**
 * Checks a library exercise the way firestore.rules does, so a mistake shows up in the form instead of as a refused
 * write. Returns the cleaned document when it is valid, or a message for each field that is not.
 */
export function validateLibraryExercise(input: LibraryInput): LibraryCheck {
  const errors: LibraryErrors = {};
  const text = (v: unknown): string => (typeof v === 'string' ? v.trim() : '');
  const name = text(input.name);
  const muscleGroup = text(input.muscleGroup);
  const equipment = text(input.equipment);
  const instructions = text(input.instructions);
  const videoUrl = text(input.videoUrl);
  const defaultSets = Number(input.defaultSets);
  const defaultReps = Number(input.defaultReps);
  const defaultWeightKg = Number(input.defaultWeightKg);

  if (!name) errors.name = 'Give the exercise a name.';
  else if (name.length > 100) errors.name = 'The name can be at most 100 characters.';
  if (!muscleGroup) errors.muscleGroup = 'Choose a muscle group.';
  else if (muscleGroup.length > 40) errors.muscleGroup = 'The muscle group can be at most 40 characters.';
  if (equipment.length > 40) errors.equipment = 'The equipment can be at most 40 characters.';
  if (instructions.length > 4000) errors.instructions = 'The instructions can be at most 4000 characters.';
  if (!Number.isInteger(defaultSets) || defaultSets < 1 || defaultSets > 20) errors.defaultSets = 'Sets must be a whole number from 1 to 20.';
  if (!Number.isInteger(defaultReps) || defaultReps < 1 || defaultReps > 100) errors.defaultReps = 'Reps must be a whole number from 1 to 100.';
  if (!Number.isFinite(defaultWeightKg) || defaultWeightKg < 0 || defaultWeightKg > 1000) errors.defaultWeightKg = 'Weight must be from 0 to 1000 kg.';
  if (videoUrl && !/^https:\/\/\S+$/i.test(videoUrl)) errors.videoUrl = 'A video link must start with https://';

  if (Object.keys(errors).length > 0) return { ok: false, errors };
  return {
    ok: true,
    value: {
      name,
      muscleGroup,
      equipment,
      level: LEVELS.includes(input.level) ? input.level : '',
      instructions,
      tips: text(input.tips).slice(0, 1000),
      defaultSets,
      defaultReps,
      defaultWeightKg,
      videoUrl,
      active: input.active !== false,
    },
  };
}

/** The starter exercises that are not in the library yet (matched by id), so importing never overwrites an edit. */
export function missingFromLibrary<T extends { id?: string }>(seed: (T | null | undefined)[], existingIds: string[]): T[] {
  const have = new Set(existingIds);
  return seed.filter((exercise): exercise is T => !!exercise && !!exercise.id && !have.has(exercise.id));
}

export interface LibraryFilters { query: string; group: string; status: 'all' | 'active' | 'hidden' }

/** Library exercises matching a search text, a muscle group and a visibility, in the usual group order. */
export function filterLibrary(exercises: LibraryExercise[], { query, group, status }: LibraryFilters): LibraryExercise[] {
  const text = query.trim().toLowerCase();
  const order = (g: string): number => { const i = MUSCLE_GROUPS.indexOf(g); return i < 0 ? MUSCLE_GROUPS.length : i; };
  return exercises
    .filter((e) => (!group || e.muscleGroup === group) && (status === 'all' || (status === 'active') === !!e.active))
    .filter((e) => !text || [e.name, e.muscleGroup, e.equipment].some((f) => typeof f === 'string' && f.toLowerCase().includes(text)))
    .sort((a, b) => order(a.muscleGroup) - order(b.muscleGroup) || String(a.name).localeCompare(String(b.name)));
}
