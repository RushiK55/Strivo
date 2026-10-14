// Pure functions of the dashboard: no React, no Firebase. Covered by logic.test.ts.
import type {
  ActivityEvent, LibraryExercise, LibraryFields, LibraryInput, SessionExercise, SessionSet, Tone, UserStatus, UserSummary, WeightRow,
} from './types';

/** How each kind of activity the app records is shown. Unknown kinds still show, with a neutral label. */
export const EVENT_TYPES: Record<string, { label: string; tone: Tone }> = {
  workout_finished: { label: 'Workout', tone: 'good' },
  plan_created: { label: 'Plan', tone: 'info' },
  plan_copied: { label: 'Plan', tone: 'info' },
  plan_deleted: { label: 'Plan', tone: 'warn' },
  plan_assigned: { label: 'Plan', tone: 'good' },
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

/** The clients matching what was typed, by name or email, ignoring case. (Not the last action: an old line may name a food.) */
export function matchesUser(user: Pick<UserSummary, 'name' | 'email'>, query: string): boolean {
  const text = query.trim().toLowerCase();
  if (!text) return true;
  return [user.name, user.email].some((field) => typeof field === 'string' && field.toLowerCase().includes(text));
}

/** The calendar day (UTC, like every time on the dashboard) as YYYY-MM-DD. */
const dayKey = (ms: number): string => new Date(ms).toISOString().slice(0, 10);

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

/**
 * The text of an activity line that is safe to show a trainer. What a client eats is private, and the first versions of
 * the app wrote the food's name into the line ("Logged "Dal and rice" (560 kcal) as dinner"); those lines are shown as
 * "Logged a meal (560 kcal)" so an old line can never reveal a food. Newer lines carry no name, and pass through.
 */
export function safeSummary(type: string, summary: string): string {
  if (type !== 'food_logged') return summary;
  if (/^Logged (breakfast|lunch|dinner|snack) \(/.test(summary)) return summary;
  const kcal = /\((\d+) kcal/.exec(summary)?.[1];
  return kcal ? `Logged a meal (${kcal} kcal)` : 'Logged a meal';
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

// ---- Library ----

export const MUSCLE_GROUPS = ['Chest', 'Back', 'Shoulders', 'Biceps', 'Triceps', 'Legs', 'Glutes', 'Core', 'Cardio', 'Full body'];
export const LEVELS = ['Beginner', 'Intermediate', 'Advanced'];
export const MAX_MUSCLES = 8;

/** A muscle group id as it is written in the catalogue: lower case words joined by dashes ("delt-front"). */
const MUSCLE_ID = /^[a-z]+(-[a-z]+)*$/;

/** The distinct, well-formed muscle ids of a list, in order. Anything else in it is dropped. */
export function cleanMuscles(value: unknown): string[] {
  return [...new Set((Array.isArray(value) ? value : []).filter((id): id is string => typeof id === 'string' && MUSCLE_ID.test(id)))];
}

export const MAX_SUBTITLE = 60;
export const MAX_ABOUT = 1000;

export type MuscleInfoCheck =
  | { ok: true; /** What to store: both empty when nothing of the muscle is overridden. */ value: { subtitle: string; about: string }; empty: boolean }
  | { ok: false; errors: { subtitle?: string; about?: string } };

/**
 * Checks what an admin wrote about a muscle the way firestore.rules does. Text equal to the built-in description is not an
 * override (it is stored empty, so a later improvement to the built-in text still shows); [empty] says nothing is overridden at
 * all, so the muscle's document can be removed.
 */
export function validateMuscleInfo(input: { subtitle: string; about: string }, builtInAbout: string): MuscleInfoCheck {
  const subtitle = input.subtitle.replace(/\s+/g, ' ').trim();
  const about = input.about.trim();
  const errors: { subtitle?: string; about?: string } = {};
  if (subtitle.length > MAX_SUBTITLE) errors.subtitle = `Keep the subtitle to ${MAX_SUBTITLE} characters or fewer (it is ${subtitle.length}).`;
  if (about.length > MAX_ABOUT) errors.about = `Keep the description to ${MAX_ABOUT} characters or fewer (it is ${about.length}).`;
  if (errors.subtitle || errors.about) return { ok: false, errors };
  const own = about === builtInAbout.trim() ? '' : about;
  return { ok: true, value: { subtitle, about: own }, empty: !subtitle && !own };
}

export type MuscleRole = 'primary' | 'secondary' | null;

/**
 * The exercise's muscles with muscle [id] made a main muscle, a helper, or neither: it can only be one of them, so it is taken out
 * of the other list first. Refuses when a list would be longer than the rules allow.
 */
export function withMuscleRole(
  exercise: { primaryMuscles?: string[]; secondaryMuscles?: string[] },
  id: string,
  role: MuscleRole,
): { ok: true; primaryMuscles: string[]; secondaryMuscles: string[] } | { ok: false; error: string } {
  const primary = cleanMuscles(exercise.primaryMuscles).filter((muscle) => muscle !== id);
  const secondary = cleanMuscles(exercise.secondaryMuscles).filter((muscle) => muscle !== id && !primary.includes(muscle));
  if (role === 'primary') primary.push(id);
  if (role === 'secondary') secondary.push(id);
  if (primary.length > MAX_MUSCLES) return { ok: false, error: `An exercise can have at most ${MAX_MUSCLES} main muscles.` };
  if (secondary.length > MAX_MUSCLES) return { ok: false, error: `An exercise can have at most ${MAX_MUSCLES} helping muscles.` };
  return { ok: true, primaryMuscles: primary, secondaryMuscles: secondary };
}

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
  const primaryMuscles = cleanMuscles(input.primaryMuscles);
  // a muscle that is a main one is not also listed as a helper
  const secondaryMuscles = cleanMuscles(input.secondaryMuscles).filter((id) => !primaryMuscles.includes(id));
  if (primaryMuscles.length > MAX_MUSCLES) errors.primaryMuscles = `Choose at most ${MAX_MUSCLES} main muscles.`;
  if (secondaryMuscles.length > MAX_MUSCLES) errors.secondaryMuscles = `Choose at most ${MAX_MUSCLES} helping muscles.`;

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
      primaryMuscles,
      secondaryMuscles,
    },
  };
}

/**
 * Exercises already in the library that have no muscles yet, for which the starter library knows them: what "Fill in
 * muscles" writes. Exercises that already have muscles are never touched, so an edit is not undone.
 */
export function musclesToFill(
  seed: { id?: string; primaryMuscles?: string[]; secondaryMuscles?: string[] }[],
  existing: Pick<LibraryExercise, 'id' | 'primaryMuscles'>[],
): { id: string; primaryMuscles: string[]; secondaryMuscles: string[] }[] {
  const without = new Set(existing.filter((exercise) => !(exercise.primaryMuscles?.length)).map((exercise) => exercise.id));
  return seed.flatMap((exercise) => {
    const primary = cleanMuscles(exercise.primaryMuscles);
    if (!exercise.id || !without.has(exercise.id) || !primary.length) return [];
    return [{ id: exercise.id, primaryMuscles: primary, secondaryMuscles: cleanMuscles(exercise.secondaryMuscles).filter((id) => !primary.includes(id)) }];
  });
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

// ---- Charts and small display helpers ----

/** Actions per calendar day for the last `days` days, oldest first, with zero for quiet days. */
export function actionsPerDay(events: Pick<ActivityEvent, 'at'>[], days: number, now: number = Date.now()): { date: string; count: number }[] {
  const counts = new Map<string, number>();
  for (const event of events) counts.set(dayKey(event.at), (counts.get(dayKey(event.at)) ?? 0) + 1);
  return Array.from({ length: days }, (_, i) => {
    const date = dayKey(now - (days - 1 - i) * DAY);
    return { date, count: counts.get(date) ?? 0 };
  });
}

/** What users do most: the number of actions per kind ("Workout", "Plan", ...), most common first. */
export function kindBreakdown(events: Pick<ActivityEvent, 'type'>[]): { label: string; count: number; share: number }[] {
  const counts = new Map<string, number>();
  for (const event of events) counts.set(eventLabel(event.type), (counts.get(eventLabel(event.type)) ?? 0) + 1);
  return [...counts.entries()]
    .map(([label, count]) => ({ label, count, share: events.length ? count / events.length : 0 }))
    .sort((a, b) => b.count - a.count || a.label.localeCompare(b.label));
}

/** How many users are in each status, for the filter on the users page. */
export function statusCounts(users: Pick<UserSummary, 'lastActiveAt'>[], now: number = Date.now()): Record<UserStatus, number> {
  const counts: Record<UserStatus, number> = { online: 0, today: 0, week: 0, inactive: 0, never: 0 };
  for (const user of users) counts[userStatus(user.lastActiveAt, now)] += 1;
  return counts;
}

/** One or two letters for an avatar: from the name, else from the email. */
export function initials(name: string | undefined, email: string | undefined): string {
  const words = (name ?? '').trim().split(/\s+/).filter(Boolean);
  const [first, second] = words;
  if (first && second) return (first.charAt(0) + second.charAt(0)).toUpperCase();
  if (first) return first.slice(0, 2).toUpperCase();
  const local = (email ?? '').split('@')[0] ?? '';
  return local ? local.slice(0, 2).toUpperCase() : '?';
}
