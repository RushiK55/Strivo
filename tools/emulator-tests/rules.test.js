// Tests of firestore.rules, run against the Firestore emulator (npm test).
const { test, before, after, beforeEach } = require('node:test');
const fs = require('node:fs');
const path = require('node:path');
const { initializeTestEnvironment, assertFails, assertSucceeds } = require('@firebase/rules-unit-testing');
const { doc, getDoc, setDoc, deleteDoc, getDocs, collection, collectionGroup, query, orderBy, limit } = require('firebase/firestore');

let env;

const goodExercise = (over = {}) => ({
  name: 'Bench Press',
  muscleGroup: 'Chest',
  equipment: 'Barbell',
  instructions: 'Lie on the bench, lower the bar to your chest and press it back up.',
  defaultSets: 3,
  defaultReps: 10,
  defaultWeightKg: 40,
  active: true,
  ...over,
});

before(async () => {
  env = await initializeTestEnvironment({
    projectId: 'strivo-test',
    firestore: { rules: fs.readFileSync(path.join(__dirname, '../../firestore.rules'), 'utf8') },
  });
});

after(async () => {
  await env.cleanup();
});

beforeEach(async () => {
  await env.clearFirestore();
  // The first admin is created by hand in the console, which the rules do not apply to.
  await env.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await setDoc(doc(db, 'admins/adminUid'), { email: 'admin@example.com' });
    await setDoc(doc(db, 'users/alice'), { email: 'alice@example.com' });
    await setDoc(doc(db, 'users/alice/sessions/1'), { planName: 'Push Day' });
    await setDoc(doc(db, 'users/alice/events/1'), { type: 'workout_finished', at: 2 });
    await setDoc(doc(db, 'users/bob/events/1'), { type: 'plan_created', at: 1 });
    await setDoc(doc(db, 'library/bench'), goodExercise());
  });
});

const as = (uid) => env.authenticatedContext(uid).firestore();
const anon = () => env.unauthenticatedContext().firestore();

// --- Signed out ---

test('a signed-out visitor can read and write nothing', async () => {
  const db = anon();
  await assertFails(getDoc(doc(db, 'users/alice')));
  await assertFails(getDoc(doc(db, 'library/bench')));
  await assertFails(getDoc(doc(db, 'admins/adminUid')));
  await assertFails(setDoc(doc(db, 'users/alice'), { x: 1 }));
});

// --- A user and their own data ---

test('a user reads and writes everything under their own id', async () => {
  const db = as('alice');
  await assertSucceeds(getDoc(doc(db, 'users/alice')));
  await assertSucceeds(setDoc(doc(db, 'users/alice'), { email: 'alice@example.com', lastActiveAt: 5 }));
  await assertSucceeds(setDoc(doc(db, 'users/alice/plans/9'), { planName: 'Legs' }));
  await assertSucceeds(setDoc(doc(db, 'users/alice/events/9'), { type: 'plan_created', at: 9 }));
  await assertSucceeds(getDocs(collection(db, 'users/alice/sessions')));
  await assertSucceeds(deleteDoc(doc(db, 'users/alice/plans/9')));
});

test('a user cannot see or change another user', async () => {
  const db = as('alice');
  await assertFails(getDoc(doc(db, 'users/bob')));
  await assertFails(getDocs(collection(db, 'users/bob/events')));
  await assertFails(setDoc(doc(db, 'users/bob/plans/1'), { planName: 'x' }));
  await assertFails(setDoc(doc(db, 'users/bob'), { email: 'x' }));
  await assertFails(deleteDoc(doc(db, 'users/bob/events/1')));
});

test('a user cannot read everyone through a collection group query', async () => {
  await assertFails(getDocs(query(collectionGroup(as('alice'), 'events'), orderBy('at', 'desc'), limit(10))));
});

test('a user cannot make themselves an admin, or read who is one', async () => {
  const db = as('alice');
  await assertFails(setDoc(doc(db, 'admins/alice'), { email: 'alice@example.com' }));
  await assertFails(getDoc(doc(db, 'admins/adminUid')));
  await assertSucceeds(getDoc(doc(db, 'admins/alice'))); // their own (missing) record: lets the app ask "am I an admin?"
});

// --- The library ---

test('any signed-in user reads the library but cannot change it', async () => {
  const db = as('alice');
  await assertSucceeds(getDoc(doc(db, 'library/bench')));
  await assertSucceeds(getDocs(collection(db, 'library')));
  await assertFails(setDoc(doc(db, 'library/new'), goodExercise()));
  await assertFails(setDoc(doc(db, 'library/bench'), goodExercise({ name: 'Hacked' })));
  await assertFails(deleteDoc(doc(db, 'library/bench')));
});

// --- Admins ---

test('an admin reads every user but cannot change their data', async () => {
  const db = as('adminUid');
  await assertSucceeds(getDoc(doc(db, 'users/alice')));
  await assertSucceeds(getDocs(collection(db, 'users')));
  await assertSucceeds(getDoc(doc(db, 'users/alice/sessions/1')));
  await assertSucceeds(getDocs(collection(db, 'users/bob/events')));
  await assertFails(setDoc(doc(db, 'users/alice/sessions/1'), { planName: 'Edited by admin' }));
  await assertFails(setDoc(doc(db, 'users/alice'), { email: 'x' }));
  await assertFails(deleteDoc(doc(db, 'users/alice/events/1')));
});

test('an admin reads the activity of all users in one query', async () => {
  const snap = await assertSucceeds(getDocs(query(collectionGroup(as('adminUid'), 'events'), orderBy('at', 'desc'), limit(10))));
  if (snap.size !== 2) throw new Error(`expected 2 events across users, got ${snap.size}`);
});

test('an admin manages the library, and the shape of an exercise is checked', async () => {
  const db = as('adminUid');
  await assertSucceeds(setDoc(doc(db, 'library/squat'), goodExercise({ name: 'Squat', muscleGroup: 'Legs' })));
  await assertSucceeds(setDoc(doc(db, 'library/squat'), goodExercise({ name: 'Back Squat', muscleGroup: 'Legs', active: false })));
  await assertSucceeds(deleteDoc(doc(db, 'library/squat')));
  await assertFails(setDoc(doc(db, 'library/a'), goodExercise({ name: '' })));
  await assertFails(setDoc(doc(db, 'library/b'), goodExercise({ defaultSets: 0 })));
  await assertFails(setDoc(doc(db, 'library/c'), goodExercise({ defaultReps: 'ten' })));
  await assertFails(setDoc(doc(db, 'library/d'), goodExercise({ defaultWeightKg: -5 })));
  await assertFails(setDoc(doc(db, 'library/e'), goodExercise({ active: 'yes' })));
  await assertFails(setDoc(doc(db, 'library/f'), { name: 'Only a name' }));
});

test('an admin can add another admin', async () => {
  const db = as('adminUid');
  await assertSucceeds(setDoc(doc(db, 'admins/second'), { email: 'second@example.com' }));
  await assertSucceeds(getDoc(doc(db, 'admins/second')));
  await assertSucceeds(getDocs(query(collectionGroup(as('second'), 'events'), orderBy('at', 'desc'), limit(10))));
});

test('everything not listed is denied, even to an admin', async () => {
  const db = as('adminUid');
  await assertFails(setDoc(doc(db, 'somethingElse/1'), { x: 1 }));
  await assertFails(getDoc(doc(db, 'somethingElse/1')));
});
