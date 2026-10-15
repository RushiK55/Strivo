// The shapes of what the app writes to Firestore, as the dashboard reads them. Fields are optional where an older
// version of the app, or a user who has not synced yet, may not have written them.

/**
 * users/{uid}/stats: a short training summary of a client that the app keeps up to date (the figures a trainer sees in
 * the client list). Older versions of the app do not write it.
 */
export interface ClientStats {
  workouts7d: number;
  workouts30d: number;
  lastWorkoutAt?: number;
  /** Weekdays the client has a plan for. */
  plannedDays: number;
  weightKg?: number;
  /** Latest weight minus the first weigh-in of the last 30 days. */
  weightChange30dKg?: number;
  avgCalories7d?: number;
  avgProteinG7d?: number;
  updatedAt?: number;
}

/** users/{uid}: a short summary the app keeps up to date while it syncs. */
export interface UserSummary {
  uid: string;
  stats?: ClientStats;
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
  /** A weekday, or 'Prebuilt' for one of the client's own plans (the ones they put on a day). */
  planDay?: string;
  /** On a weekday's plan: the planId of the client's own plan it was made from. */
  sourcePlanId?: number | null;
  /** On a client's own plan: where it came from, 'library:<id>' (the plan library) or 'trainer:<id>' (a plan sent from here); empty when they made it. */
  origin?: string;
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
  /** The plan's sets as JSON text (weight and reps of each); present once the client has changed a set. */
  setsData?: string;
}

export interface WeightRow {
  id: string;
  date?: string;
  weight?: number | string;
  isDemo?: number;
}

/** users/{uid}/nutrition/{date}: one day's calorie and protein totals. Never says what was eaten. */
export interface NutritionDay {
  id: string;
  date: string;
  calories: number;
  protein?: number;
  carbs?: number;
  fat?: number;
  meals?: number;
}

/** A finished workout of any client, from the all-clients query: who it belongs to comes with it. */
export interface ClientWorkout extends WorkoutRow {
  uid: string;
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
  /** Muscle group ids (models/anatomy/muscles.json) the exercise mainly trains, and the ones that help. Older exercises have none. */
  primaryMuscles?: string[];
  secondaryMuscles?: string[];
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
  primaryMuscles?: string[];
  secondaryMuscles?: string[];
}

/** The part of a library exercise an admin edits (no id or timestamps). */
export type LibraryFields = Omit<LibraryExercise, 'id' | 'createdAt' | 'updatedAt' | 'updatedBy'>;

/** One exercise of a plan a trainer sends. */
export interface AssignedExercise {
  name: string;
  sets: number;
  reps: number;
  weightKg: number;
  dropSets: number;
  notes: string;
}

/** The part of a plan in the plan library an admin edits (no id or timestamps). */
export interface LibraryPlanFields {
  name: string;
  description: string;
  level: string;
  active: boolean;
  exercises: AssignedExercise[];
}

/** planLibrary/{planId}: a plan every user can browse in the app and put on a day, built from the exercise library. */
export interface LibraryPlan extends LibraryPlanFields {
  id: string;
  createdAt?: number;
  updatedAt?: number;
  updatedBy?: string;
}

/** What the trainer fills in when sending a plan. */
export interface AssignmentFields {
  planName: string;
  /** A weekday, or 'Prebuilt' for the client's library of plans. */
  day: string;
  note: string;
  sentByName: string;
  exercises: AssignedExercise[];
}

/**
 * users/{uid}/assigned/{id}: a plan a trainer sent. 'sent' until it reaches the client's phone ('delivered'), where the client
 * accepts it (it goes into their plans, on the day the trainer chose: 'accepted') or declines it ('declined'). Only the trainer
 * writes it (and may withdraw it until it is answered); only the app changes its status, and an answer is final.
 */
export interface Assignment extends AssignmentFields {
  id: string;
  status: 'sent' | 'delivered' | 'accepted' | 'declined';
  sentAt: number;
  sentBy: string;
  deliveredAt?: number;
  answeredAt?: number;
}

/**
 * muscles/{muscleId}: what an admin wrote about a muscle of the 3D body. `subtitle` is a friendlier name shown under the muscle's
 * name, `about` a description that replaces the built-in one; either may be empty (the built-in text is used). A muscle with nothing
 * written has no document.
 */
export interface MuscleInfo {
  id: string;
  subtitle?: string;
  about?: string;
  updatedAt?: number;
  updatedBy?: string;
}

/** admins/{uid} */
export interface AdminEntry {
  id: string;
  email?: string;
  addedAt?: number;
  addedBy?: string;
}

export type Tone = 'neutral' | 'good' | 'info' | 'warn' | 'bad';
export type UserStatus = 'online' | 'today' | 'week' | 'inactive' | 'never';
