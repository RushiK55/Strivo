// Tests of firestore.rules, run against the Firestore emulator (npm test).
const { test, before, after, beforeEach } = require('node:test');
const fs = require('node:fs');
const path = require('node:path');
const { initializeTestEnvironment, assertFails, assertSucceeds } = require('@firebase/rules-unit-testing');
const { doc, getDoc, setDoc, updateDoc, deleteDoc, getDocs, collection, collectionGroup, query, orderBy, limit } = require('firebase/firestore');

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
    await setDoc(doc(db, 'users/alice/food/1'), { name: 'Dal and rice', calories: 560 });
    await setDoc(doc(db, 'users/alice/nutrition/2026-10-12'), { date: '2026-10-12', calories: 2000, protein: 120 });
    await setDoc(doc(db, 'users/alice/weights/2026-10-12'), { date: '2026-10-12', weight: 71 });
    await setDoc(doc(db, 'users/alice/history/1'), { name: 'Bench Press' });
    await setDoc(doc(db, 'users/bob/events/1'), { type: 'plan_created', at: 1 });
    await setDoc(doc(db, 'library/bench'), goodExercise());
  });
});

const as = (uid) => env.authenticatedContext(uid).firestore();

const sentPlan = (over = {}) => ({
  planName: 'Push Day',
  day: 'Monday',
  note: 'Go light on the first set.',
  exercises: [{ name: 'Bench Press', sets: 4, reps: 8, weightKg: 60, dropSets: 0, notes: '' }],
  status: 'sent',
  sentAt: 1,
  sentBy: 'adminUid',
  sentByName: 'Coach',
  ...over,
});
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

test('an admin (the trainer) reads training, body and daily intake totals, but never the food log', async () => {
  const db = as('adminUid');
  for (const path of ['sessions/1', 'events/1', 'nutrition/2026-10-12', 'weights/2026-10-12']) {
    await assertSucceeds(getDoc(doc(db, 'users/alice/' + path)));
  }
  await assertSucceeds(getDocs(collection(db, 'users/alice/nutrition')));
  // What someone eats, and the exercise history, stay private to the client.
  await assertFails(getDoc(doc(db, 'users/alice/food/1')));
  await assertFails(getDocs(collection(db, 'users/alice/food')));
  await assertFails(getDocs(query(collectionGroup(db, 'food'), limit(10))));
  await assertFails(getDoc(doc(db, 'users/alice/history/1')));
  // The client still reads their own food log, and nobody else can.
  await assertSucceeds(getDoc(doc(as('alice'), 'users/alice/food/1')));
  await assertFails(getDoc(doc(as('bob'), 'users/alice/food/1')));
  await assertFails(setDoc(doc(db, 'users/alice/nutrition/2026-10-12'), { calories: 1 }));
});

test('an admin reads all finished workouts in one query, a client cannot', async () => {
  const snap = await assertSucceeds(getDocs(query(collectionGroup(as('adminUid'), 'sessions'), limit(10))));
  if (snap.size !== 1) throw new Error('expected 1 workout across clients, got ' + snap.size);
  await assertFails(getDocs(query(collectionGroup(as('alice'), 'sessions'), limit(10))));
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

test('an exercise may say which muscles it trains, as short lists, and older ones without muscles still save', async () => {
  const db = as('adminUid');
  await assertSucceeds(setDoc(doc(db, 'library/pull-up'), goodExercise({ name: 'Pull-Up', muscleGroup: 'Back', primaryMuscles: ['lats'], secondaryMuscles: ['biceps', 'mid-back'] })));
  await assertSucceeds(setDoc(doc(db, 'library/pull-up'), { primaryMuscles: ['lats', 'biceps'], secondaryMuscles: [] }, { merge: true }));
  await assertSucceeds(setDoc(doc(db, 'library/old'), goodExercise({ name: 'Without muscles' })));
  await assertFails(setDoc(doc(db, 'library/g'), goodExercise({ primaryMuscles: 'lats' })));
  await assertFails(setDoc(doc(db, 'library/h'), goodExercise({ secondaryMuscles: { lats: true } })));
  await assertFails(setDoc(doc(db, 'library/i'), goodExercise({ primaryMuscles: ['a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i'] })));
  // a client still cannot change them
  await assertFails(setDoc(doc(as('alice'), 'library/pull-up'), { primaryMuscles: ['chest'] }, { merge: true }));
});

test('an admin can add another admin', async () => {
  const db = as('adminUid');
  await assertSucceeds(setDoc(doc(db, 'admins/second'), { email: 'second@example.com' }));
  await assertSucceeds(getDoc(doc(db, 'admins/second')));
  await assertSucceeds(getDocs(query(collectionGroup(as('second'), 'events'), orderBy('at', 'desc'), limit(10))));
});

// --- Plans a trainer sends ---

test('a trainer sends a plan to a client, and only a plan of the right shape', async () => {
  const db = as('adminUid');
  await assertSucceeds(setDoc(doc(db, 'users/alice/assigned/a1'), sentPlan()));
  await assertSucceeds(getDocs(collection(db, 'users/alice/assigned')));
  await assertFails(setDoc(doc(db, 'users/alice/assigned/a2'), sentPlan({ day: 'Someday' })));
  await assertFails(setDoc(doc(db, 'users/alice/assigned/a3'), sentPlan({ exercises: [] })));
  await assertFails(setDoc(doc(db, 'users/alice/assigned/a4'), sentPlan({ planName: '' })));
  await assertFails(setDoc(doc(db, 'users/alice/assigned/a5'), sentPlan({ status: 'delivered' })));
  await assertFails(setDoc(doc(db, 'users/alice/assigned/a6'), sentPlan({ sentBy: 'someoneElse' })));
  await assertFails(setDoc(doc(db, 'users/alice/assigned/a7'), sentPlan({ extra: true })));
  // A trainer still cannot change the client's own plans, only send one.
  await assertFails(setDoc(doc(db, 'users/alice/plans/1'), { planName: 'x' }));
  // Someone who is not a trainer cannot send one.
  await assertFails(setDoc(doc(as('bob'), 'users/alice/assigned/a8'), sentPlan({ sentBy: 'bob' })));
});

test('the client reads a sent plan and can only mark it delivered', async () => {
  await assertSucceeds(setDoc(doc(as('adminUid'), 'users/alice/assigned/a1'), sentPlan()));
  const db = as('alice');
  await assertSucceeds(getDocs(query(collection(db, 'users/alice/assigned'))));
  await assertFails(setDoc(doc(db, 'users/alice/assigned/a1'), sentPlan({ planName: 'Changed' })));
  await assertFails(updateDoc(doc(db, 'users/alice/assigned/a1'), { status: 'delivered', deliveredAt: 5, planName: 'Changed' }));
  await assertFails(setDoc(doc(db, 'users/alice/assigned/mine'), sentPlan({ sentBy: 'alice' })));
  await assertFails(deleteDoc(doc(db, 'users/alice/assigned/a1')));
  await assertSucceeds(updateDoc(doc(db, 'users/alice/assigned/a1'), { status: 'delivered', deliveredAt: 5 }));
  // Another user sees nothing of it.
  await assertFails(getDoc(doc(as('bob'), 'users/alice/assigned/a1')));
});

test('a trainer can withdraw a plan until the app has picked it up', async () => {
  const db = as('adminUid');
  await assertSucceeds(setDoc(doc(db, 'users/alice/assigned/a1'), sentPlan()));
  await assertSucceeds(deleteDoc(doc(db, 'users/alice/assigned/a1')));
  await assertSucceeds(setDoc(doc(db, 'users/alice/assigned/a2'), sentPlan()));
  await assertSucceeds(updateDoc(doc(as('alice'), 'users/alice/assigned/a2'), { status: 'delivered', deliveredAt: 5 }));
  await assertFails(deleteDoc(doc(db, 'users/alice/assigned/a2')));
  await assertFails(updateDoc(doc(db, 'users/alice/assigned/a2'), { status: 'sent' }));
});

test('everything not listed is denied, even to an admin', async () => {
  const db = as('adminUid');
  await assertFails(setDoc(doc(db, 'somethingElse/1'), { x: 1 }));
  await assertFails(getDoc(doc(db, 'somethingElse/1')));
});
