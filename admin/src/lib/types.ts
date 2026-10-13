// The shapes of what the app writes to Firestore, as the dashboard reads them. Fields are optional where an older
// version of the app, or a user who has not synced yet, may not have written them.

/** users/{uid}: a short summary the app keeps up to date while it syncs. */
export interface UserSummary {
  uid: string;
  name?: string;
  email?: string;
  lastActiveAt?: number;
  createdAt?: number;
  appVersion?: string;
  platform?: string;
  lastEvent?: string;
  lastEventAt?: number;
  lastEventType?: string;
}

/** users/{uid}/events/{id}: one thing the user did, in plain words. */
export interface ActivityEvent {
  id: string;
  uid: string;
  at: number;
  type: string;
  summary: string;
}

/** One set of an exercise in a finished workout (the app stores sessions as JSON text). */
export interface SessionSet {
  weight?: number;
  reps?: number;
  isCompleted?: boolean;
  /** Absent for a normal set; warmup, drop or failure otherwise. */
  type?: 'warmup' | 'drop' | 'failure' | string;
}

export interface SessionExercise {
  name: string;
  sets?: SessionSet[];
}

/** users/{uid}/sessions/{id}. `exercises` is JSON text written by the phone. */
export interface WorkoutRow {
  id: string;
  planName?: string;
  date?: string;
  totalTime?: string;
  exercises?: string | SessionExercise[];
  isDemo?: number;
}

export interface PlanRow {
  id: string;
  planId?: number;
  planName?: string;
  planDay?: string;
  isDemo?: number;
}

export interface ExerciseRow {
  id: string;
  planId?: number;
  name?: string;
  weight?: string;
  sets?: string;
  reps?: string;
  notes?: string;
  dropSets?: number;
  sortOrder?: number;
}

export interface WeightRow {
  id: string;
  date?: string;
  weight?: number | string;
}

export interface FoodRow {
  id: string;
  date?: string;
  meal?: string;
  name?: string;
  calories?: number | string;
  isDemo?: number;
}

/** users/{uid}/meta/profile */
export interface ProfileDoc {
  gender?: string;
  age?: number;
  height?: number;
  weight?: number;
  restDays?: string[];
}

/** library/{exerciseId}: what every user can browse in the app. */
export interface LibraryExercise {
  id: string;
  name: string;
  muscleGroup: string;
  equipment: string;
  level: string;
  instructions: string;
  tips: string;
  defaultSets: number;
  defaultReps: number;
  defaultWeightKg: number;
  videoUrl: string;
  active: boolean;
  createdAt?: number;
  updatedAt?: number;
  updatedBy?: string;
}

/** What the library form edits, before it is checked: everything is text until validated. */
export interface LibraryInput {
  name: string;
  muscleGroup: string;
  equipment: string;
  level: string;
  instructions: string;
  tips: string;
  defaultSets: string | number;
  defaultReps: string | number;
  defaultWeightKg: string | number;
  videoUrl: string;
  active: boolean;
}

/** The part of a library exercise an admin edits (no id or timestamps). */
export type LibraryFields = Omit<LibraryExercise, 'id' | 'createdAt' | 'updatedAt' | 'updatedBy'>;

/** admins/{uid} */
export interface AdminEntry {
  id: string;
  email?: string;
  addedAt?: number;
  addedBy?: string;
}

export type Tone = 'neutral' | 'good' | 'info' | 'warn';
export type UserStatus = 'online' | 'today' | 'week' | 'inactive' | 'never';
