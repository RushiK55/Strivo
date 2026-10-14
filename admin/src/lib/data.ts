// Every Firestore read and write the dashboard makes. The admin only ever WRITES to the library, the admins list and the
// plans it sends a client (users/{uid}/assigned); the rest of a client's data is read-only to an admin (firestore.rules
// enforces this, not just this code).
import {
  addDoc, collection, collectionGroup, deleteDoc, doc, getCountFromServer, getDoc, getDocs, limit, orderBy, query, setDoc, startAfter,
  where, writeBatch, type DocumentData, type OrderByDirection, type QueryDocumentSnapshot, type Query,
} from 'firebase/firestore';
import { getFirebase } from './firebase';
import type { ActivityEvent, AdminEntry, Assignment, AssignmentFields, ClientWorkout, LibraryExercise, LibraryFields, NutritionDay, ProfileDoc, UserSummary } from './types';

const PAGE = 50;

/** Where the next page starts: the last document of the previous one. */
export type Cursor = QueryDocumentSnapshot<DocumentData>;

export interface Page<T> { items: T[]; last: Cursor | null; hasMore: boolean }

const db = () => getFirebase().db;
const withId = <T>(snapshot: QueryDocumentSnapshot<DocumentData>): T => ({ id: snapshot.id, ...snapshot.data() }) as T;

// ---- Admins ----

export async function isAdmin(uid: string): Promise<boolean> {
  return (await getDoc(doc(db(), 'admins', uid))).exists();
}

export async function listAdmins(): Promise<AdminEntry[]> {
  const snap = await getDocs(collection(db(), 'admins'));
  return snap.docs.map((d) => withId<AdminEntry>(d));
}

export async function addAdmin(uid: string, email: string, addedBy: string): Promise<void> {
  await setDoc(doc(db(), 'admins', uid), { email, addedBy, addedAt: Date.now() });
}

export async function removeAdmin(uid: string): Promise<void> {
  await deleteDoc(doc(db(), 'admins', uid));
}

// ---- Users ----

/** One page of users, the most recently active first. Pass the `last` of the previous page to continue. */
export async function listUsers(after: Cursor | null = null): Promise<Page<UserSummary>> {
  const parts = [orderBy('lastActiveAt', 'desc'), limit(PAGE), ...(after ? [startAfter(after)] : [])];
  const snap = await getDocs(query(collection(db(), 'users'), ...parts));
  return { items: snap.docs.map((d) => ({ ...d.data(), uid: d.id }) as UserSummary), last: snap.docs.at(-1) ?? null, hasMore: snap.size === PAGE };
}

export async function getUser(uid: string): Promise<UserSummary | null> {
  const snap = await getDoc(doc(db(), 'users', uid));
  return snap.exists() ? ({ ...snap.data(), uid }) as UserSummary : null;
}

export async function getProfile(uid: string): Promise<ProfileDoc | null> {
  const snap = await getDoc(doc(db(), 'users', uid, 'meta', 'profile'));
  return snap.exists() ? (snap.data() as ProfileDoc) : null;
}

const count = async (q: Query<DocumentData>): Promise<number> => (await getCountFromServer(q)).data().count;

export const countUsers = () => count(collection(db(), 'users'));
export const countActiveUsers = (sinceMs: number) => count(query(collection(db(), 'users'), where('lastActiveAt', '>=', sinceMs)));
export const countEventsSince = (sinceMs: number) => count(query(collectionGroup(db(), 'events'), where('at', '>=', sinceMs)));
export const countLibrary = () => count(collection(db(), 'library'));
export const countOf = (uid: string, name: string) => count(collection(db(), 'users', uid, name));

// ---- Activity ----

/**
 * One page of activity, newest first: one user's when `uid` is given, otherwise everyone's (a collection group query).
 * Each event carries the uid of the user it belongs to.
 */
export async function listEvents({ uid = null, after = null, pageSize = PAGE }: { uid?: string | null; after?: Cursor | null; pageSize?: number } = {}): Promise<Page<ActivityEvent>> {
  const source = uid ? collection(db(), 'users', uid, 'events') : collectionGroup(db(), 'events');
  const parts = [orderBy('at', 'desc'), limit(pageSize), ...(after ? [startAfter(after)] : [])];
  const snap = await getDocs(query(source, ...parts));
  return {
    items: snap.docs.map((d) => ({ ...d.data(), id: d.id, uid: d.ref.parent.parent?.id ?? '' }) as ActivityEvent),
    last: snap.docs.at(-1) ?? null,
    hasMore: snap.size === pageSize,
  };
}

// ---- What a user has saved ----

/** The newest rows of one of a user's collections (sessions, plans, exercises, weights, food). */
export async function listUserRows<T>(
  uid: string,
  name: string,
  { orderField = null, direction = 'desc', max = 100 }: { orderField?: string | null; direction?: OrderByDirection; max?: number } = {},
): Promise<T[]> {
  const parts = orderField ? [orderBy(orderField, direction), limit(max)] : [limit(max)];
  const snap = await getDocs(query(collection(db(), 'users', uid, name), ...parts));
  return snap.docs.map((d) => withId<T>(d));
}

// ---- Library ----

export async function listLibrary(): Promise<LibraryExercise[]> {
  const snap = await getDocs(collection(db(), 'library'));
  return snap.docs.map((d) => withId<LibraryExercise>(d));
}

/** Creates or updates one exercise. `createdAt` is only set the first time. */
export async function saveLibraryExercise(id: string, value: LibraryFields, adminUid: string, isNew: boolean): Promise<void> {
  const now = Date.now();
  const data = { ...value, updatedAt: now, updatedBy: adminUid, ...(isNew ? { createdAt: now } : {}) };
  await setDoc(doc(db(), 'library', id), data, { merge: true });
}

export async function setLibraryActive(id: string, active: boolean, adminUid: string): Promise<void> {
  await setDoc(doc(db(), 'library', id), { active, updatedAt: Date.now(), updatedBy: adminUid }, { merge: true });
}

export async function deleteLibraryExercise(id: string): Promise<void> {
  await deleteDoc(doc(db(), 'library', id));
}

/** Sets the muscles of library exercises (and nothing else of them), in batches. Returns how many were written. */
export async function setLibraryMuscles(updates: { id: string; primaryMuscles: string[]; secondaryMuscles: string[] }[], adminUid: string): Promise<number> {
  const now = Date.now();
  let written = 0;
  for (let i = 0; i < updates.length; i += 400) {
    const batch = writeBatch(db());
    for (const { id, ...muscles } of updates.slice(i, i + 400)) {
      batch.set(doc(db(), 'library', id), { ...muscles, updatedAt: now, updatedBy: adminUid }, { merge: true });
      written += 1;
    }
    await batch.commit();
  }
  return written;
}

/** Writes the given exercises in batches (Firestore allows 500 writes per batch). Returns how many were written. */
export async function importLibrary(exercises: (LibraryFields & { id: string })[], adminUid: string): Promise<number> {
  const now = Date.now();
  let written = 0;
  for (let i = 0; i < exercises.length; i += 400) {
    const batch = writeBatch(db());
    for (const { id, ...fields } of exercises.slice(i, i + 400)) {
      batch.set(doc(db(), 'library', id), { ...fields, createdAt: now, updatedAt: now, updatedBy: adminUid });
      written += 1;
    }
    await batch.commit();
  }
  return written;
}

// ---- Plans sent to a client ----

/** The plans trainers sent this client, newest first. */
export async function listAssignments(uid: string, max = 50): Promise<Assignment[]> {
  const snap = await getDocs(query(collection(db(), 'users', uid, 'assigned'), orderBy('sentAt', 'desc'), limit(max)));
  return snap.docs.map((d) => withId<Assignment>(d));
}

/** Sends a plan. The client's app adds it to their plans on its next sync. */
export async function sendAssignment(uid: string, value: AssignmentFields, adminUid: string): Promise<void> {
  await addDoc(collection(db(), 'users', uid, 'assigned'), { ...value, status: 'sent', sentAt: Date.now(), sentBy: adminUid });
}

/** Takes a plan back; the rules only allow it while the app has not picked it up. */
export async function withdrawAssignment(uid: string, id: string): Promise<void> {
  await deleteDoc(doc(db(), 'users', uid, 'assigned', id));
}

// ---- Training and intake (what a trainer coaches from) ----

/**
 * Every client's finished workouts since a date (YYYY-MM-DD), newest first, with the client each belongs to.
 * Demo workouts are left out. One query (a collection group query), so the attendance page does not read client by client.
 */
export async function listWorkoutsSince(date: string, max = 1000): Promise<ClientWorkout[]> {
  const snap = await getDocs(query(collectionGroup(db(), 'sessions'), where('date', '>=', date), orderBy('date', 'desc'), limit(max)));
  return snap.docs
    .map((d) => ({ ...d.data(), id: d.id, uid: d.ref.parent.parent?.id ?? '' }) as ClientWorkout)
    .filter((workout) => !workout.isDemo);
}

/** One client's daily calorie and protein totals, newest first. There is no way to read what was eaten. */
export async function listNutrition(uid: string, max = 90): Promise<NutritionDay[]> {
  const snap = await getDocs(query(collection(db(), 'users', uid, 'nutrition'), orderBy('date', 'desc'), limit(max)));
  return snap.docs.map((d) => withId<NutritionDay>(d));
}
