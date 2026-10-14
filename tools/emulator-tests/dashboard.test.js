// The admin dashboard (the Next.js build) in a real browser (headless Chrome or Edge), talking to the Firebase Auth and
// Firestore emulators. Run with: npm test   (it starts the emulators and builds the dashboard itself)
const { test, before, after } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const http = require('node:http');
const { execSync } = require('node:child_process');
const path = require('node:path');
const puppeteer = require('puppeteer-core');
const { initializeTestEnvironment } = require('@firebase/rules-unit-testing');
const { doc, setDoc, updateDoc, getDoc, getDocs, collection } = require('firebase/firestore');

const ADMIN_DIR = path.join(__dirname, '../../admin');
// The test build goes to its own folder, so it can never replace the build that is meant for the real project.
const BUILD_DIR = '.test-build';
const SITE_DIR = path.join(ADMIN_DIR, BUILD_DIR);
const FIREBASE_JSON = JSON.parse(fs.readFileSync(path.join(__dirname, '../../firebase.json'), 'utf8'));
const SEED = JSON.parse(fs.readFileSync(path.join(ADMIN_DIR, 'seed/library.json'), 'utf8'));
const NOW = Date.now();
const MIN = 60_000;

const CHROME = [process.env.CHROME_PATH, 'C:/Program Files/Google/Chrome/Application/chrome.exe', 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',
  '/usr/bin/google-chrome', '/usr/bin/chromium'].find((p) => p && fs.existsSync(p));

const TYPES = { '.html': 'text/html', '.js': 'text/javascript', '.css': 'text/css', '.json': 'application/json', '.txt': 'text/plain', '.svg': 'image/svg+xml', '.ico': 'image/x-icon', '.glb': 'model/gltf-binary', '.md': 'text/markdown' };

/**
 * Builds the dashboard pointing at the emulators (the settings are baked in at build time). With SKIP_BUILD=1 the build that is
 * already in the test folder is used: on a machine short of memory, build first (the same command, from admin/ with the
 * NEXT_PUBLIC_FIREBASE_* values below and ADMIN_BUILD_DIR=.test-build), then run the tests with the emulator started.
 */
function buildDashboard() {
  if (process.env.SKIP_BUILD && fs.existsSync(path.join(SITE_DIR, 'index.html'))) return;
  execSync('npm run build', {
    cwd: ADMIN_DIR,
    stdio: 'pipe',
    env: {
      ...process.env,
      ADMIN_BUILD_DIR: BUILD_DIR,
      NEXT_PUBLIC_FIREBASE_API_KEY: 'fake-key',
      NEXT_PUBLIC_FIREBASE_AUTH_DOMAIN: 'localhost',
      NEXT_PUBLIC_FIREBASE_PROJECT_ID: 'strivo-test',
      NEXT_PUBLIC_FIREBASE_APP_ID: 'test',
      NEXT_PUBLIC_FIREBASE_EMULATORS: '1',
    },
  });
}

let server, baseUrl, browser, page, testEnv;
const problems = []; // anything the browser complained about: console errors, CSP violations, uncaught exceptions
const users = {};

/** Serves the built dashboard like Firebase Hosting would: same security headers, and /users/ finds users/index.html. */
function startServer() {
  const headers = {};
  for (const rule of FIREBASE_JSON.hosting.headers.filter((r) => r.source === '**')) {
    for (const { key, value } of rule.headers) headers[key] = value;
  }
  // The emulators live on localhost, which the real policy rightly does not allow.
  headers['Content-Security-Policy'] = headers['Content-Security-Policy'].replace("connect-src 'self'", "connect-src 'self' http://127.0.0.1:* http://localhost:*");
  return new Promise((resolve) => {
    const s = http.createServer((req, res) => {
      const url = new URL(req.url, 'http://localhost');
      const send = (status, type, body) => { res.writeHead(status, { 'content-type': type, ...headers }); res.end(body); };
      let file = path.join(SITE_DIR, decodeURIComponent(url.pathname));
      if (file.startsWith(SITE_DIR) && fs.existsSync(file) && fs.statSync(file).isDirectory()) file = path.join(file, 'index.html');
      if (!file.startsWith(SITE_DIR) || !fs.existsSync(file)) return send(404, 'text/html', fs.readFileSync(path.join(SITE_DIR, '404.html')));
      return send(200, TYPES[path.extname(file)] || 'application/octet-stream', fs.readFileSync(file));
    });
    s.listen(0, '127.0.0.1', () => resolve(s));
  });
}

async function signUp(email, password) {
  const response = await fetch('http://127.0.0.1:9099/identitytoolkit.googleapis.com/v1/accounts:signUp?key=fake-key', {
    method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify({ email, password, returnSecureToken: true }),
  });
  return (await response.json()).localId;
}

const DAY_MS = 86_400_000;
/** The calendar day (UTC) of `daysAgo` days before now, as YYYY-MM-DD. */
const dayOf = (daysAgo) => new Date(NOW - daysAgo * DAY_MS).toISOString().slice(0, 10);
/** A workout date the way the phone writes it: 2026-10-12T07:30:00. */
const when = (daysAgo, time = '07:30:00') => `${dayOf(daysAgo)}T${time}`;
const set = (weight, reps, extra = {}) => ({ weight, reps, isCompleted: true, ...extra });
// A food whose name must never appear anywhere in the dashboard.
const SECRET_FOOD = 'Secret Dal';

async function seed() {
  users.admin = await signUp('admin@example.com', 'admin-pass-1');
  users.alice = await signUp('alice@example.com', 'alice-pass-1');
  users.bob = await signUp('bob@example.com', 'bob-pass-1');
  users.outsider = await signUp('outsider@example.com', 'outsider-pass-1');
  users.carol = 'carolUid0000000001';
  testEnv = await initializeTestEnvironment({ projectId: 'strivo-test', firestore: { rules: fs.readFileSync(path.join(__dirname, '../../firestore.rules'), 'utf8') } });
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    const put = (p, data) => setDoc(doc(db, p), data);
    await put(`admins/${users.admin}`, { email: 'admin@example.com', addedAt: NOW });

    // Alice trains regularly and is gaining strength; Bob has stopped coming; Carol has not got the newest app yet.
    await put(`users/${users.alice}`, {
      email: 'alice@example.com', name: 'Alice Active', lastActiveAt: NOW - 3 * MIN, createdAt: NOW - 20 * DAY_MS, appVersion: '1.1.0', platform: 'android',
      lastEvent: `Logged "${SECRET_FOOD}" (560 kcal) as dinner`, lastEventAt: NOW - 3 * MIN, lastEventType: 'food_logged',
      stats: { workouts7d: 3, workouts30d: 11, lastWorkoutAt: NOW - 5 * 3_600_000, plannedDays: 4, weightKg: 71, weightChange30dKg: -1.5, avgCalories7d: 2020, avgProteinG7d: 115, updatedAt: NOW },
    });
    await put(`users/${users.bob}`, {
      email: 'bob@example.com', name: 'Bob Inactive', lastActiveAt: NOW - 30 * DAY_MS, createdAt: NOW - 90 * DAY_MS, appVersion: '1.1.0', platform: 'android',
      stats: { workouts7d: 0, workouts30d: 2, lastWorkoutAt: NOW - 12 * DAY_MS, plannedDays: 3, weightKg: 82, weightChange30dKg: 0.5, updatedAt: NOW },
    });
    await put(`users/${users.carol}`, { email: 'carol@example.com', name: 'Carol Newcomer', lastActiveAt: NOW - 2 * DAY_MS, createdAt: NOW - 5 * DAY_MS, appVersion: '1.0.0', platform: 'android' });

    const a = `users/${users.alice}`;
    await put(`${a}/events/1`, { id: 1, at: NOW - 3 * MIN, type: 'food_logged', summary: `Logged "${SECRET_FOOD}" (560 kcal) as dinner` }); // how an older app wrote it
    await put(`${a}/events/2`, { id: 2, at: NOW - 50 * MIN, type: 'workout_finished', summary: 'Finished "Push Day": 2 exercises, 750 kg, 42 min' });
    await put(`${a}/events/3`, { id: 3, at: NOW - 2 * DAY_MS, type: 'plan_created', summary: 'Created plan "Push Day" for Monday' });
    await put(`${a}/events/4`, { id: 4, at: NOW - 3 * DAY_MS, type: 'exercise_added', summary: 'Added "<img src=x onerror=window.__xss=1>Curl" to Push Day (Monday) · 3 × 10' });
    await put(`${a}/events/5`, { id: 5, at: NOW - 4 * DAY_MS, type: 'food_logged', summary: 'Logged lunch (620 kcal, 41 g protein)' }); // how the current app writes it
    await put(`users/${users.bob}/events/1`, { id: 1, at: NOW - 30 * DAY_MS, type: 'weight_logged', summary: 'Weighed in at 82 kg' });

    // Alice's workouts: the bench press goes 55 -> 57.5 -> 60 -> 62.5 kg over two weeks.
    const session = (id, daysAgo, planName, exercises, totalTime = '00:42:14') => put(`${a}/sessions/${id}`, { id, planName, date: when(daysAgo), totalTime, isDemo: 0, exercises: JSON.stringify(exercises) });
    await session(1, 0, 'Push Day', [
      { name: 'Bench Press', sets: [set(30, 10, { type: 'warmup' }), set(62.5, 6), set(62.5, 6)] },
      { name: 'Push-Up', sets: [set(0, 20)] },
    ]);
    await session(2, 2, 'Pull Day', [{ name: 'Barbell Row', sets: [set(50, 10), set(50, 10)] }]);
    await session(3, 4, 'Push Day', [{ name: 'Bench Press', sets: [set(60, 8), set(60, 8)] }]);
    await session(4, 7, 'Push Day', [{ name: 'Bench Press', sets: [set(57.5, 8), set(57.5, 8)] }]);
    await session(5, 14, 'Push Day', [{ name: 'Bench Press', sets: [set(55, 8), set(55, 8)] }]);
    await put(`${a}/sessions/6`, { id: 6, planName: 'Demo Day', date: when(1), totalTime: '00:10:00', isDemo: 1, exercises: '[]' }); // demo data is never counted
    // Bob's last workouts: 12 and 20 days ago.
    await put(`users/${users.bob}/sessions/1`, { id: 1, planName: 'Leg Day', date: when(12), totalTime: '00:50:00', isDemo: 0, exercises: JSON.stringify([{ name: 'Squat', sets: [set(80, 5)] }]) });
    await put(`users/${users.bob}/sessions/2`, { id: 2, planName: 'Leg Day', date: when(20), totalTime: '00:50:00', isDemo: 0, exercises: JSON.stringify([{ name: 'Squat', sets: [set(75, 5)] }]) });

    await put(`${a}/plans/1`, { planId: 1, planName: 'Push Day', planDay: 'Monday', isDemo: 0 });
    await put(`${a}/plans/2`, { planId: 2, planName: 'Push Day', planDay: 'Prebuilt', isDemo: 0 });
    await put(`${a}/exercises/1`, { id: 1, planId: 1, name: 'Bench Press', weight: '60.0', sets: '4', reps: '8', notes: 'Keep wrists straight', dropSets: 1, sortOrder: 0 });
    await put(`${a}/exercises/2`, { id: 2, planId: 1, name: 'Push-Up', weight: '0.0', sets: '3', reps: '15', notes: '', dropSets: 0, sortOrder: 1 });
    await put(`${a}/weights/${dayOf(28)}`, { date: dayOf(28), weight: 72.5 });
    await put(`${a}/weights/${dayOf(14)}`, { date: dayOf(14), weight: 71.8 });
    await put(`${a}/weights/${dayOf(1)}`, { date: dayOf(1), weight: 71.0 });
    await put(`${a}/meta/profile`, { gender: 'Female', age: 29, height: 168.0, weight: 71.0, restDays: ['SUNDAY'], updatedAt: 1 });

    // The food log is the client's own backup: the dashboard has no right to read it. What a trainer sees are the totals.
    await put(`${a}/food/1`, { id: 1, date: dayOf(0), meal: 'Dinner', name: SECRET_FOOD, calories: 560, isDemo: 0 });
    const intake = [[0, 2100, 130], [1, 1900, 110], [2, 2300, undefined], [3, 1800, 100], [4, 2000, 120]];
    for (const [daysAgo, calories, protein] of intake) {
      await put(`${a}/nutrition/${dayOf(daysAgo)}`, { date: dayOf(daysAgo), calories, ...(protein ? { protein } : {}), carbs: 220, fat: 70, meals: 4, updatedAt: NOW });
    }

    const exercise = (id, name, group) => ({ name, muscleGroup: group, equipment: 'Barbell', level: 'Beginner', instructions: 'Do it carefully and with control.', tips: '', defaultSets: 3, defaultReps: 10, defaultWeightKg: 20, videoUrl: '', active: true, createdAt: NOW, updatedAt: NOW });
    await put('library/bench-press', exercise('bench-press', 'Bench Press', 'Chest'));
    await put('library/old-move', { ...exercise('old-move', 'Old Move', 'Legs'), active: false });
  });
}

// ---- Small helpers for driving the page ----

/** With SCREENSHOTS=<folder> the test saves a picture of each main page, for looking at the design. */
const shot = async (name) => { if (process.env.SCREENSHOTS) await page.screenshot({ path: path.join(process.env.SCREENSHOTS, name + '.png'), fullPage: true }); };

const text = () => page.evaluate(() => document.body.innerText);

/** A page that prints the words null or undefined is showing a value that was never set. */
const assertNoLeaks = async (where) => assert.doesNotMatch(await text(), /\b(null|undefined|NaN)\b/, where + ' shows a value that was never set');
/** Waits until the page shows the text; a timeout says what the page showed instead. */
async function waitForText(t, timeout = 15_000) {
  try {
    await page.waitForFunction((s) => document.body.innerText.includes(s), { timeout }, t);
  } catch (error) {
    throw new Error(`Timed out waiting for "${t}". The page showed:
${(await text()).slice(0, 1800)}`);
  }
}
/** Dialogs fade out for a moment after they close; a click in that moment would land on the backdrop. */
const waitForNoDialog = () => page.waitForFunction(() => !document.querySelector('[role="dialog"], [role="alertdialog"]'), { timeout: 15_000 });
const waitForGone = (t, timeout = 15_000) => page.waitForFunction((s) => !document.body.innerText.includes(s), { timeout }, t);

/** Clicks the first visible element of a kind whose text matches. */
async function clickText(selector, label) {
  let handle;
  try {
    handle = await page.waitForFunction((sel, l) => [...document.querySelectorAll(sel)].find((e) => e.innerText.trim() === l || e.textContent.trim() === l), { timeout: 15_000 }, selector, label);
  } catch {
    const found = await page.evaluate((sel) => [...document.querySelectorAll(sel)].map((e) => e.innerText.trim().slice(0, 40)), selector);
    throw new Error(`No ${selector} with the text "${label}". Found: ${JSON.stringify(found)}`);
  }
  await handle.asElement().click();
}

async function signIn(email, password) {
  await page.goto(`${baseUrl}/`);
  await page.waitForSelector('input[name="email"]');
  await page.type('input[name="email"]', email);
  await page.type('input[name="password"]', password);
  await clickText('button', 'Sign in');
}

before(async () => {
  assert.ok(CHROME, 'No Chrome or Edge found. Set CHROME_PATH to a browser executable.');
  buildDashboard();
  server = await startServer();
  baseUrl = `http://127.0.0.1:${server.address().port}`;
  await seed();
  browser = await puppeteer.launch({ executablePath: CHROME, headless: true, args: ['--no-sandbox', '--disable-gpu', '--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist'] });
  page = await browser.newPage();
  await page.setViewport({ width: 1280, height: 900 });
  page.on('pageerror', (error) => problems.push(`pageerror: ${error.message}`));
  page.on('console', (message) => {
    if (message.type() === 'error') problems.push(`console: ${message.text()}`);
  });
  page.on('dialog', (dialog) => dialog.dismiss());
});

after(async () => {
  if (browser) await browser.close();
  if (server) server.close();
  if (testEnv) await testEnv.cleanup();
});

// ---- The tests, in the order a trainer would use the dashboard ----

/** Nothing on any page may say what a client eats. */
const assertPrivate = async (where) => assert.doesNotMatch(await text(), /Secret/, where + ' shows what a client ate');

/** Clicks a button of a filter row by the start of its text (the count is part of it: "Missing 1"). */
async function pickFilter(prefix) {
  const handle = await page.waitForFunction((p) => [...document.querySelectorAll('[data-slot="toggle-group-item"]')].find((e) => e.innerText.trim().startsWith(p)), { timeout: 15_000 }, prefix);
  await handle.asElement().click();
}

test('a user who is not an admin is turned away', async () => {
  await signIn('outsider@example.com', 'outsider-pass-1');
  await waitForText('This account is not an admin');
  assert.ok(!(await text()).includes('Overview'));
});

test('a wrong password says so', async () => {
  await page.goto(`${baseUrl}/`);
  await page.waitForSelector('input[name="email"]');
  await page.type('input[name="email"]', 'admin@example.com');
  await page.type('input[name="password"]', 'not-the-password');
  await clickText('button', 'Sign in');
  await waitForText('Wrong email or password.');
});

test('the trainer signs in and sees who is training and who needs a nudge', async () => {
  await page.click('input[name="password"]', { clickCount: 3 });
  await page.type('input[name="password"]', 'admin-pass-1');
  await clickText('button', 'Sign in');
  await waitForText('Needs a nudge');
  await waitForText('Latest workouts');
  const body = await text();
  assert.match(body, /Clients\s*\n?\s*3/, 'three clients');
  assert.match(body, /Trained in the last 7 days\s*\n?\s*1|1\s*\n?\s*Trained in the last 7 days/, 'only Alice trained this week');
  assert.ok(body.includes('Alice Active did Push Day'), 'the latest workouts name the client');
  assert.ok(body.includes('Bob Inactive') && body.includes('Missing'), 'Bob, who stopped coming, needs a nudge');
  assert.ok(!body.includes('Demo Day'), 'demo workouts are not counted');
  await assertNoLeaks('the overview');
  await assertPrivate('the overview');
  await shot('overview');
});

test('the clients page shows training, body weight and eating, and filters', async () => {
  await clickText('nav[aria-label="Main"] a', 'Clients');
  await waitForText('All clients');
  let body = await text();
  assert.ok(body.includes('Alice Active') && body.includes('alice@example.com') && body.includes('Bob Inactive') && body.includes('Carol Newcomer'));
  assert.ok(body.includes('On track') && body.includes('Missing') && body.includes('No data yet'));
  assert.ok(body.includes('3 of 4 days'), 'this week against the plan');
  assert.ok(body.includes('71 kg') && body.includes('-1.5 kg in 30 days'), 'body weight and how it moved');
  assert.ok(body.includes('2,020 kcal') && body.includes('115 g protein'), 'calories and protein, as totals');
  await assertNoLeaks('the clients page');
  await assertPrivate('the clients page');
  await shot('clients');

  await pickFilter('Missing');
  await waitForGone('Alice Active');
  body = await text();
  assert.ok(body.includes('Bob Inactive') && !body.includes('Carol Newcomer'));
  await pickFilter('All');
  await waitForText('Alice Active');
  await page.type('input[type="search"]', 'carol');
  await waitForGone('Alice Active');
  assert.ok((await text()).includes('Carol Newcomer'));
  await page.click('input[type="search"]', { clickCount: 3 });
  await page.keyboard.press('Backspace');
  await waitForText('Alice Active');
});

test('the attendance page shows who came in the last two weeks, and who did not', async () => {
  await clickText('nav[aria-label="Main"] a', 'Attendance');
  await waitForText('Last 14 days');
  await waitForText('Missed this week');
  let body = await text();
  assert.ok(body.includes('Alice Active') && body.includes('Bob Inactive'));
  assert.ok(body.includes('3 of 4 days'), 'Alice came three days this week');
  assert.ok(body.includes('0 of 3 days'), 'Bob came none of his three planned days');
  const alice = await page.evaluate(() => [...document.querySelectorAll('tbody tr')].find((tr) => tr.innerText.includes('Alice Active')).querySelectorAll('span.bg-primary').length);
  assert.equal(alice, 4, 'Alice trained 4 of the last 14 days (the demo workout is not counted)');
  await assertNoLeaks('the attendance page');
  await shot('attendance');

  await pickFilter('Missed this week');
  await waitForGone('Alice Active');
  body = await text();
  assert.ok(body.includes('Bob Inactive') && body.includes('Carol Newcomer'));
  await pickFilter('All');
  await waitForText('Alice Active');
});

test('a client page shows attendance, strength, body weight, intake, workouts and plans', async () => {
  await clickText('nav[aria-label="Main"] a', 'Clients');
  await waitForText('All clients');
  await page.click('a[href*="/clients/view/"]'); // the most recently active client is listed first: Alice
  await waitForText('Workouts per week');
  let body = await text();
  assert.ok(body.includes('3 of 4') && body.includes('75% of the plan'), 'this week against the plan');
  assert.ok(body.includes('Workouts, last 30 days'));
  assert.ok(await page.evaluate(() => document.querySelectorAll('[role="img"][aria-label="Workout calendar of the last 12 weeks"] span').length) === 84, 'a calendar of 12 weeks');
  await assertNoLeaks('the overview tab');
  await shot('client-overview');

  await clickText('button[role="tab"]', 'Strength');
  await waitForText('Heaviest set of each workout: 55 → 62.5 kg (+7.5 kg) over 4 workouts');
  body = await text();
  assert.ok(body.includes('Bench Press') && body.includes('Barbell Row') && body.includes('Push-Up'), 'every lift is listed');
  assert.ok(body.includes('+7.5 kg'));
  await page.select('select[aria-label="Exercise"]', 'Push-Up');
  await waitForText('Most reps in a set of each workout: 20 → 20 reps');
  await shot('client-strength');

  await clickText('button[role="tab"]', 'Body');
  await waitForText('Body weight');
  await waitForText('72.5 kg → ');
  body = await text();
  assert.ok(body.includes('Female') && body.includes('168 cm'));
  assert.ok(body.includes('71 kg'));
  assert.ok(body.includes('-1.5 kg'), 'the change over the whole period');
  await assertNoLeaks('the body tab');
  await shot('client-body');

  await clickText('button[role="tab"]', 'Nutrition');
  await waitForText('Daily totals');
  body = await text();
  assert.ok(body.includes('2,020 kcal') && body.includes('115 g'), 'averages of what was logged (a day without protein is left out of the protein average)');
  assert.ok(body.includes('You see how much your client eats, not what.'));
  assert.ok(body.includes('2,300 kcal'), 'a day in the table');
  await assertNoLeaks('the nutrition tab');
  await assertPrivate('the nutrition tab');
  await shot('client-nutrition');

  await clickText('button[role="tab"]', 'Workouts');
  await waitForText('Push Day');
  await page.click('[data-slot="accordion-trigger"]');
  await waitForText('W 30 × 10, 62.5 × 6, 62.5 × 6');
  body = await text();
  assert.ok(body.includes('750 kg'), 'warm-ups are left out of the volume');
  assert.ok(body.includes('42 min'));
  assert.ok(!body.includes('Demo Day'), 'demo workouts are not shown');

  await clickText('button[role="tab"]', 'Plans');
  await waitForText('Keep wrists straight');
  body = await text();
  assert.ok(body.includes('4 × 8 + 1 drop @ 60.0 kg') || body.includes('4 × 8 + 1 drop @ 60 kg'));
  assert.ok(body.includes('Library'), 'the library copy of the plan is labelled');

  await clickText('button[role="tab"]', 'Activity');
  await waitForText('Created plan "Push Day" for Monday');
  body = await text();
  assert.ok(body.includes('Logged a meal (560 kcal)'), 'a food line an older app wrote is shown without the food');
  assert.ok(body.includes('Logged lunch (620 kcal, 41 g protein)'), 'a line without a food name is shown as it is');
  await assertPrivate('the activity tab');

  // Activity written by a client is shown as text, never run as HTML.
  assert.ok(body.includes('<img src=x onerror=window.__xss=1>Curl'), 'the markup is displayed as plain text');
  assert.equal(await page.evaluate(() => window.__xss), undefined, 'the script in that text never ran');
  assert.equal(await page.evaluate(() => document.querySelectorAll('img[src="x"]').length), 0);
  await assertNoLeaks('the activity tab');
  await shot('client-activity');
});

test('the trainer sends a client a plan, sees it reach the app, and can withdraw one that has not', async () => {
  await page.goto(`${baseUrl}/clients/view/?uid=${users.alice}`);
  await waitForText('Workouts per week');
  await clickText('button[role="tab"]', 'Plans');
  await waitForText('Plans you sent');
  await waitForText('Nothing sent yet.');
  await waitForText('Keep wrists straight'); // the client's own plans, offered to start from
  await clickText('button', 'Send a plan');
  await page.waitForSelector('[role="dialog"] input[name="planName"]');

  // An empty form says what is missing instead of sending.
  await clickText('[role="dialog"] button', 'Send plan');
  await waitForText('Give the plan a name.');
  await waitForText('Add at least one exercise.');

  // Starting from one of the client's plans copies its exercises.
  const pushDay = await page.evaluate(() => [...document.querySelectorAll('[role="dialog"] select option')].find((o) => o.textContent.startsWith('Push Day (Monday'))?.value);
  assert.ok(pushDay, "the client's plans are offered to start from");
  await page.select('[role="dialog"] select', pushDay);
  assert.equal(await page.$eval('input[name="planName"]', (e) => e.value), 'Push Day');
  await waitForGone('Give the plan a name.'); // a fixed field stops being marked without sending again
  await waitForGone('Add at least one exercise.');
  await page.$eval('input[name="planName"]', (e) => { e.select(); });
  await page.type('input[name="planName"]', 'Heavier Push');
  await page.select('select[name="day"]', 'Thursday');
  await page.type('textarea[name="note"]', 'Add 2.5 kg to the bench this week.');
  await page.$eval('input[name="sentByName"]', (e) => { e.select(); });
  await page.type('input[name="sentByName"]', 'Coach Sam');
  await shot('send-plan');
  await clickText('[role="dialog"] button', 'Send plan');
  await waitForNoDialog();
  await waitForText('Waiting for their app');
  let body = await text();
  assert.ok(body.includes('Heavier Push') && body.includes('Thursday') && body.includes('by Coach Sam'));
  await assertNoLeaks('the plans tab');

  // What was written is exactly what the app reads.
  let sent;
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    sent = (await getDocs(collection(ctx.firestore(), `users/${users.alice}/assigned`))).docs;
  });
  assert.equal(sent.length, 1);
  const plan = sent[0].data();
  assert.equal(plan.status, 'sent');
  assert.equal(plan.sentBy, users.admin);
  assert.equal(plan.day, 'Thursday');
  assert.ok(plan.exercises.length >= 1 && plan.exercises.every((e) => Number.isInteger(e.sets) && typeof e.weightKg === 'number'));

  // The client's app picks it up and marks it delivered (as the client, so the rules are checked too): it is on their phone,
  // waiting for them to accept it, so the trainer can still take it back.
  const alice = testEnv.authenticatedContext(users.alice).firestore();
  const assigned = doc(alice, `users/${users.alice}/assigned/${sent[0].id}`);
  await updateDoc(assigned, { status: 'delivered', deliveredAt: Date.now() });
  await clickText('button[role="tab"]', 'Overview');
  await clickText('button[role="tab"]', 'Plans');
  await waitForText('waiting for an answer');
  assert.ok((await text()).includes('Withdraw'), 'a plan the client has not answered can still be withdrawn');
  // The client accepts it: the trainer sees that, and it can no longer be withdrawn.
  await updateDoc(assigned, { status: 'accepted', answeredAt: Date.now() });
  await clickText('button[role="tab"]', 'Overview');
  await clickText('button[role="tab"]', 'Plans');
  await waitForText('Accepted ·');
  assert.ok(!(await text()).includes('Withdraw'), 'an accepted plan cannot be withdrawn');

  // A second plan, withdrawn before the app saw it.
  await clickText('button', 'Send a plan');
  await page.waitForSelector('[role="dialog"] input[name="planName"]');
  await page.type('input[name="planName"]', 'Mistake');
  await page.type('input[aria-label="Exercise 1 name"]', 'Plank');
  await clickText('[role="dialog"] button', 'Send plan');
  await waitForNoDialog();
  await waitForText('Mistake');
  await clickText('button', 'Withdraw');
  await clickText('[role="alertdialog"] button', 'Withdraw');
  await waitForGone('Mistake');
  await waitForNoDialog();
  // A third the client declines: the trainer sees that too.
  await clickText('button', 'Send a plan');
  await page.waitForSelector('[role="dialog"] input[name="planName"]');
  await page.type('input[name="planName"]', 'Not for me');
  await page.type('input[aria-label="Exercise 1 name"]', 'Row');
  await clickText('[role="dialog"] button', 'Send plan');
  await waitForNoDialog();
  await waitForText('Not for me');
  let declinedId;
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    declinedId = (await getDocs(collection(ctx.firestore(), `users/${users.alice}/assigned`))).docs.find((d) => d.data().planName === 'Not for me').id;
  });
  await updateDoc(doc(alice, `users/${users.alice}/assigned/${declinedId}`), { status: 'declined', answeredAt: Date.now() });
  await clickText('button[role="tab"]', 'Overview');
  await clickText('button[role="tab"]', 'Plans');
  await waitForText('Declined ·');
  await shot('client-plans');
});

test('a client page opens straight from its address, and says so when no client is chosen', async () => {
  await page.goto(`${baseUrl}/clients/view/?uid=${users.alice}`);
  await waitForText('Alice Active');
  await waitForText('Workouts per week');
  await assertNoLeaks('a client page opened by address');

  await page.goto(`${baseUrl}/clients/view/?uid=nobody-here`);
  await waitForText('That client has no summary yet');

  await page.goto(`${baseUrl}/clients/view/?uid=${users.carol}`);
  await waitForText('Carol Newcomer');
  await waitForText('has not opened a version of the app that reports training yet');

  await page.goto(`${baseUrl}/clients/view/`);
  await waitForText('Pick a client from the Clients page.');
});

test('the activity page shows every client, filterable by kind', async () => {
  await clickText('nav[aria-label="Main"] a', 'Activity');
  await waitForText('Everything your clients add or change');
  await waitForText('Weighed in at 82 kg');
  let body = await text();
  assert.ok(body.includes('Bob Inactive') && body.includes('Alice Active'));
  assert.ok(body.includes('Logged a meal (560 kcal)'));
  await assertPrivate('the activity page');
  await pickFilter('Weight');
  await waitForGone('Created plan "Push Day"');
  body = await text();
  assert.ok(body.includes('Weighed in at 82 kg'));
  await pickFilter('Everything');
  await page.type('input[type="search"]', 'secret');
  await waitForText('No activity yet.'); // searching for the food finds nothing: the name is not even in the page
});

test('the library: add, validate, hide, import the starter set and delete', async () => {
  await clickText('nav[aria-label="Main"] a', 'Library');
  await waitForText('Exercise library');
  await waitForText('Old Move');
  assert.ok((await text()).includes('2 of 2 exercises'));
  await assertNoLeaks('the library');
  await shot('library');

  // The form refuses an empty name instead of sending it to Firestore.
  await clickText('button', 'New exercise');
  await page.waitForSelector('input[name="name"]');
  await clickText('button', 'Add to library');
  await waitForText('Give the exercise a name.');
  await shot('library-form');
  await page.type('input[name="name"]', 'Tester Curl');
  await page.select('select[name="muscleGroup"]', 'Biceps');
  await page.type('input[name="equipment"]', 'Dumbbells');
  await page.type('textarea[name="instructions"]', 'Curl the weight up and lower it slowly.');
  await page.click('input[name="defaultSets"]', { clickCount: 3 });
  await page.type('input[name="defaultSets"]', '4');
  await page.type('input[name="videoUrl"]', 'javascript:alert(1)');
  await clickText('button', 'Add to library');
  await waitForText('A video link must start with https://');
  await page.click('input[name="videoUrl"]', { clickCount: 3 });
  await page.keyboard.press('Backspace');
  await clickText('button', 'Add to library');
  await waitForText('Tester Curl added');
  await waitForText('3 of 3 exercises');

  let stored = [];
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    stored = (await getDocs(collection(ctx.firestore(), 'library'))).docs.map((d) => ({ id: d.id, ...d.data() }));
  });
  const created = stored.find((d) => d.name === 'Tester Curl');
  assert.ok(created, 'the new exercise is stored; library has ' + JSON.stringify(stored.map((d) => d.id)));
  assert.equal(created.id, 'tester-curl');
  assert.equal(created.muscleGroup, 'Biceps');
  assert.equal(created.defaultSets, 4);
  assert.equal(created.updatedBy, users.admin);
  assert.equal(created.active, true);

  // Hiding an exercise keeps it in the list but marks it hidden for users.
  const row = async (name) => page.evaluateHandle((n) => [...document.querySelectorAll('tbody tr')].find((tr) => tr.innerText.includes(n)), name);
  await (await (await row('Tester Curl')).asElement().$('[role="switch"]')).click();
  await waitForText('Tester Curl is now hidden from users');

  // The starter library is added without touching what is already there (Bench Press exists, and keeps its id).
  await clickText('button', 'Import starter library');
  await waitForText(`Add ${SEED.length - 1} starter exercises?`);
  await clickText('[role="alertdialog"] button', 'Import');
  await waitForText(`${SEED.length - 1} exercises imported`);
  await waitForText(`${SEED.length + 2} of ${SEED.length + 2} exercises`);
  await shot("library-paged");

  // The table is paged: 10 rows to a page, with buttons for the other pages and a choice of rows per page.
  const total = SEED.length + 2;
  // The messages in the corner sit over the page buttons for a few seconds; wait until they are gone.
  await page.waitForFunction(() => !document.querySelector('[data-sonner-toast]'), { timeout: 15_000 });
  await waitForText(`Showing 1–10 of ${total} exercises`);
  await page.click('a[aria-label="Page 2"]');
  await waitForText(`Showing 11–20 of ${total} exercises`);
  await page.click('a[aria-label="Go to previous page"]');
  await waitForText(`Showing 1–10 of ${total} exercises`);
  await page.select('select[aria-label="Rows per page"]', '25');
  await waitForText(`Showing 1–25 of ${total} exercises`);

  // Deleting asks first.
  await page.type('input[aria-label="Search the library"]', 'Old Move');
  await waitForText('1 of ');
  await page.click('button[aria-label="Actions for Old Move"]');
  await clickText('[role="menuitem"]', 'Delete');
  await waitForText('Delete "Old Move" from the library?');
  await clickText('[role="alertdialog"] button', 'Delete');
  await waitForText('Old Move deleted');
  await waitForText(`0 of ${SEED.length + 1} exercises`);
  await waitForNoDialog();
});

test('the body page: a full-page 3D body with a skin over the muscles, a three-line button for the controls and credits in the corners; pick a muscle, the skin comes off and a box beside it, joined by a line, explains it and shows its exercises', async () => {
  // A few library exercises that say which muscles they train (and one that does not, and one that is hidden).
  const exercise = (name, group, extra) => ({ name, muscleGroup: group, equipment: 'Bodyweight', level: 'Beginner', instructions: `How to do ${name}.`, tips: '', defaultSets: 3, defaultReps: 10, defaultWeightKg: 0, videoUrl: '', active: true, ...extra });
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await setDoc(doc(db, 'library/b-pull-up'), exercise('Body Pull-Up', 'Back', { primaryMuscles: ['lats'], secondaryMuscles: ['biceps'] }));
    await setDoc(doc(db, 'library/b-row'), exercise('Body Row', 'Back', { primaryMuscles: ['lats', 'mid-back'], secondaryMuscles: [] }));
    await setDoc(doc(db, 'library/b-curl'), exercise('Body Curl', 'Biceps', { primaryMuscles: ['biceps'], secondaryMuscles: ['lats'] }));
    await setDoc(doc(db, 'library/b-old'), exercise('Body Old Back', 'Back', {}));
    await setDoc(doc(db, 'library/b-hidden'), exercise('Body Hidden', 'Back', { primaryMuscles: ['lats'], active: false }));
  });
  const webgl = await (async () => { const probe = await browser.newPage(); const ok = await probe.evaluate(() => !!document.createElement('canvas').getContext('webgl2')); await probe.close(); return ok; })();
  const switchState = (label) => page.$eval(`[role="switch"][aria-label^="${label}"]`, (e) => e.getAttribute('aria-checked'));
  const skin = () => page.$eval('[data-testid="body-viewer"]', (e) => e.dataset.skin);
  const rect = (selector) => page.$eval(selector, (e) => { const r = e.getBoundingClientRect(); return { left: r.left, top: r.top, right: r.right, bottom: r.bottom, width: r.width, height: r.height }; });
  const openList = async () => { await page.click('button[aria-label="Open the muscle list"]'); await waitForText('Choose one, or click it on the body.'); };
  // (The box slides while the camera flies to the muscle, so wait for that to end before clicking on it: a click on a box that has just moved would land on the body.)
  const closeCard = async () => { await new Promise((resolve) => setTimeout(resolve, 1200)); await page.click('button[aria-label="Close the muscle card"]'); await page.waitForFunction(() => !document.querySelector('[data-testid="muscle-callout"]'), { timeout: 10_000 }); };

  await clickText('nav[aria-label="Main"] a', 'Body');
  await page.waitForSelector('[data-testid="body-stage"]');
  await page.waitForSelector('[data-testid="body-viewer"]'); // (the viewer's code loads a moment after the stage appears)
  // A three-line button in the top right corner of the stage opens the control panel, which is closed to begin with so the body
  // has the whole view.
  const stageBox = await rect('[data-testid="body-stage"]');
  const toggle = await rect('[data-testid="controls-toggle"]');
  assert.ok(toggle.right > stageBox.right - 30 && toggle.top < stageBox.top + 30, 'the three-line button is in the top right corner of the stage');
  assert.equal(await page.$eval('[data-testid="controls-toggle"] svg', (e) => e.getAttribute('class').includes('lucide-menu')), true, 'and it is a three-line (menu) icon');
  assert.ok(!(await page.$('[data-testid="body-controls"]')), 'the control panel is closed to begin with');
  assert.ok(!/fade surface|skeleton|full screen/i.test(await text()), 'and nothing of it shows (there is no full screen button at all)');
  await shot('body-closed');
  await page.click('[data-testid="controls-toggle"]');
  await page.waitForSelector('[data-testid="body-controls"]');
  assert.equal(await page.$eval('[data-testid="controls-toggle"]', (e) => e.getAttribute('aria-expanded')), 'true');
  const controls = await rect('[data-testid="body-controls"]');
  assert.ok(controls.right > stageBox.right - 30 && controls.top < stageBox.top + 80, 'the panel opens in the top right corner of the stage');
  assert.ok(controls.width < stageBox.width / 3, 'and it is a small cluster, not a column across the page');
  assert.ok(stageBox.width > 700, `the stage is wide (${stageBox.width}px)`);
  // The 3D view fills the page under the top bar: edge to edge and down to the bottom of the window.
  const window_ = await page.evaluate(() => ({ width: document.documentElement.clientWidth, height: window.innerHeight }));
  assert.ok(Math.abs(stageBox.right - window_.width) < 2 && Math.abs(stageBox.bottom - window_.height) < 2, `the stage fills the page to the window's corner (${JSON.stringify(stageBox)} in ${JSON.stringify(window_)})`);
  assert.ok(stageBox.height > window_.height * 0.8, 'and is most of the window tall');
  // Its background is the same plain black as the rest of the dashboard: no grey, no gradient.
  const looks = await page.$eval('[data-testid="body-stage"]', (e) => ({ stage: getComputedStyle(e).backgroundColor, image: getComputedStyle(e).backgroundImage, page: getComputedStyle(document.body).backgroundColor }));
  assert.equal(looks.image, 'none', 'no gradient behind the body');
  assert.equal(looks.stage, looks.page, 'the same background as the page');
  assert.equal(looks.stage, 'rgb(0, 0, 0)', 'which is black');
  const buttons = await page.$$eval('[data-testid="body-controls"] button', (list) => list.map((b) => b.innerText.trim()));
  for (const name of ['Muscles', 'Front', 'Back', 'Reset']) assert.ok(buttons.some((text) => text.includes(name)), `the controls have ${name}`);
  assert.equal(await skin(), 'on', 'the body starts with its skin on');
  assert.equal(await switchState('Show the skin'), 'true');
  assert.equal(await switchState('Show the skeleton'), 'false', 'and no skeleton');
  assert.ok(!(await text()).includes('Quadratus lumborum'), 'the list of muscles is closed until the Muscles button is pressed');
  await assertNoLeaks('the body page');

  // The credits are a small button in the bottom right corner of the stage, and open a panel above it.
  assert.ok(!(await text()).includes('CC BY-SA 4.0'), 'the credits are not spread over the page');
  const creditsButton = await rect('[data-testid="credits-button"]');
  assert.ok(creditsButton.right > stageBox.right - 30 && creditsButton.bottom > stageBox.bottom - 30, 'the credits button is in the bottom right corner');
  // (A notice left over from the previous page, such as "Old Move deleted", can still be sitting in that corner for a few seconds: clear it first.)
  await page.evaluate(() => document.querySelectorAll('[data-sonner-toast]').forEach((toast) => toast.remove()));
  await page.click('[data-testid="credits-button"]');
  await page.waitForSelector('[data-testid="credits-panel"]');
  const credits = await text();
  assert.ok(credits.includes('BodyParts3D') && credits.includes('Z-Anatomy') && credits.includes('CC BY-SA 4.0') && credits.includes('Human Reference Atlas'), 'the credits name every source');
  const creditsPanel = await rect('[data-testid="credits-panel"]');
  assert.ok(creditsPanel.right <= stageBox.right && creditsPanel.bottom <= creditsButton.top + 1, 'the credits panel opens above the button, inside the stage');
  await page.keyboard.press('Escape');
  await page.waitForFunction(() => !document.querySelector('[data-testid="credits-panel"]'), { timeout: 10_000 });

  // The Muscles button opens the list of every muscle, over the stage, and closes it again.
  await openList();
  assert.ok((await text()).includes('Quadratus lumborum'), 'every muscle is in the list, deep ones too');
  assert.equal(await page.$eval('[data-testid="body-controls"] button[aria-expanded]', (e) => e.getAttribute('aria-expanded')), 'true', 'the Muscles button shows that the list is open');
  await page.click('[data-testid="muscle-panel"] button[aria-label="Close the muscle list"]');
  await page.waitForFunction(() => !document.querySelector('[data-testid="muscle-panel"]'), { timeout: 10_000 });

  if (webgl) {
    await page.waitForSelector('[data-testid="body-viewer"] canvas');
    await waitForGone('Loading the 3D body');
    await waitForText('Drag to turn');
    // Hovering the body names the muscle under the pointer (through the skin), and clicking it picks that muscle: this goes
    // through the real 3D scene.
    const box = await (await page.$('[data-testid="body-viewer"]')).boundingBox();
    assert.ok(box.width > 700 && box.height > 450, 'the body has a big view');
    await shot('body-male');
    // Find a point on the body: try a few places down the middle, and wait a moment at each for the label.
    let at = null;
    for (const [fx, fy] of [[0.5, 0.3], [0.45, 0.4], [0.55, 0.5], [0.45, 0.6], [0.55, 0.7], [0.5, 0.45], [0.4, 0.5], [0.6, 0.4]]) {
      const point = { x: box.x + box.width * fx, y: box.y + box.height * fy };
      await page.mouse.move(point.x - 12, point.y - 12);
      await page.mouse.move(point.x, point.y, { steps: 3 });
      const found = await page.waitForSelector('[data-testid="hover-label"]', { timeout: 2_500 }).catch(() => null);
      if (found) { at = point; break; }
    }
    assert.ok(at, 'hovering over the body names a muscle (the screenshot body-male shows what was drawn)');
    await page.mouse.click(at.x, at.y);
    await page.waitForSelector('[data-testid="muscle-callout"]', { timeout: 15_000 });
    assert.notEqual(await page.$eval('[data-testid="body-viewer"]', (e) => e.dataset.selected), '', 'the click picked a muscle');
    assert.equal(await skin(), 'off', 'picking a muscle takes the skin off');
    assert.equal(await switchState('Show the skin'), 'false');
    // The box floats in the stage, clear of the controls, and a line joins it to the muscle.
    await page.waitForSelector('[data-testid="callout-line"]', { timeout: 10_000 });
    await new Promise((resolve) => setTimeout(resolve, 1200)); // let the camera finish flying to it
    const card = await rect('[data-testid="muscle-callout"]');
    const now = await rect('[data-testid="body-stage"]');
    assert.ok(card.left >= now.left && card.right <= now.right && card.top >= now.top && card.bottom <= now.bottom, 'the box is inside the stage');
    const controlsNow = await rect('[data-testid="body-controls"]');
    assert.ok(card.right <= controlsNow.left || card.bottom <= controlsNow.top || card.left >= controlsNow.right, 'and does not cover the controls');
    const line = await page.$eval('[data-testid="callout-line"] line', (e) => ({ x1: +e.getAttribute('x1'), y1: +e.getAttribute('y1'), x2: +e.getAttribute('x2'), y2: +e.getAttribute('y2') }));
    assert.ok(line.x1 >= 0 && line.x1 <= now.width && line.y1 >= 0 && line.y1 <= now.height, `the line starts on the muscle, inside the view (${JSON.stringify(line)})`);
    assert.ok(Math.abs(line.x2 - (card.right - now.left)) < 2 || Math.abs(line.x2 - (card.left - now.left)) < 2, 'and ends on the edge of the box');
    await shot('body-callout');
    await page.click('button:has(svg.lucide-rotate-ccw)');
    await page.waitForFunction(() => !document.querySelector('[data-testid="muscle-callout"]'), { timeout: 10_000 });
    assert.equal(await skin(), 'on', 'resetting puts the skin back');
  } else {
    assert.ok((await text()).includes('cannot show 3D'), 'without WebGL the page says so and the list still works');
  }

  // Choose a muscle from the list: the list closes, and the box says what it is, with the exercises behind its button.
  await openList();
  await page.click('[data-muscle="lats"]');
  await waitForText('The latissimus dorsi');
  assert.equal(await page.$eval('[data-testid="muscle-title"]', (e) => e.textContent), 'Lats');
  assert.ok(!(await page.$('[data-testid="muscle-panel"]')), 'the list closes when a muscle is picked from it');
  assert.equal(await skin(), 'off', 'the skin is off for a muscle chosen from the list too');
  // (The library also holds the starter exercises the library test imported, so the count is at least these four.)
  await waitForText('Show exercises (');
  const label = await page.evaluate(() => [...document.querySelectorAll('button')].map((b) => b.innerText.trim()).find((t) => /^Show exercises \(\d+\)$/.test(t)));
  assert.ok(Number(/\d+/.exec(label)[0]) >= 4, `the lats have at least four exercises, the button said "${label}"`);
  assert.ok(!(await text()).includes('Body Pull-Up'), 'the exercises are behind the button');
  await clickText('button', label);
  // (The section titles are shown in capitals, and innerText returns them that way.)
  const seen = (pattern) => page.waitForFunction((source) => new RegExp(source, 'i').test(document.body.innerText), { timeout: 15_000 }, pattern);
  await seen('Main exercises · \\d+');
  await seen('Also works this muscle · \\d+');
  await seen('More Back exercises · 1\\b'); // only the exercise with no muscles set is "general": the starter ones all have muscles
  const exercises = await text();
  assert.ok(exercises.includes('Body Pull-Up') && exercises.includes('Body Row') && exercises.includes('Body Curl') && exercises.includes('Body Old Back'));
  assert.ok(!exercises.includes('Body Hidden'), 'a hidden exercise is not listed');
  // the exercises are inside the box (it scrolls), which stays inside the stage
  const full = await rect('[data-testid="muscle-callout"]');
  const stageNow = await rect('[data-testid="body-stage"]');
  assert.ok(full.top >= stageNow.top && full.bottom <= stageNow.bottom, 'the box with its exercises still fits the stage');
  // the exercise opens to show how to do it (its button also holds the equipment line, so it is found by its start)
  await page.evaluate(() => [...document.querySelectorAll('button')].find((b) => b.innerText.startsWith('Body Pull-Up'))?.click());
  await waitForText('How to do Body Pull-Up.');
  await assertNoLeaks('a muscle');
  await shot('body-lats');
  if (webgl) assert.equal(await page.$eval('[data-testid="body-viewer"]', (e) => e.dataset.selected), 'lats');

  // Escape closes the box and puts the skin back; another muscle can be picked from the list.
  await page.keyboard.press('Escape');
  await page.waitForFunction(() => !document.querySelector('[data-testid="muscle-callout"]'), { timeout: 10_000 });
  assert.equal(await skin(), 'on');
  await openList();
  await page.click('[data-muscle="mid-back"]');
  await waitForText('The middle and lower trapezius');
  assert.equal(await page.$eval('[data-testid="muscle-title"]', (e) => e.textContent), 'Mid back');
  if (webgl) {
    assert.equal(await page.$eval('[data-testid="body-viewer"]', (e) => e.dataset.selected), 'mid-back');
    await new Promise((resolve) => setTimeout(resolve, 1200)); // let the camera finish flying to it
    await shot('body-mid-back');
  }

  // A deep muscle cannot be seen under the surface, so picking it fades the surface layer; picking a surface one again undoes that.
  await openList();
  await page.click('[data-muscle="quadratus-lumborum"]');
  await waitForText('Deep muscle');
  assert.equal(await switchState('Fade the surface'), 'true');
  assert.equal(await skin(), 'off');
  if (webgl) await new Promise((resolve) => setTimeout(resolve, 1200)); // let the camera finish flying to it
  await shot('body-deep');
  await openList();
  await page.click('[data-muscle="lats"]');
  await waitForText('The latissimus dorsi');
  assert.equal(await switchState('Fade the surface'), 'false', 'the fade that picking a deep muscle switched on goes off with it');

  // The skin can also be switched on and off by hand.
  await closeCard();
  assert.equal(await skin(), 'on', 'closing the box puts the skin back');
  await page.click('[role="switch"][aria-label="Show the skin"]');
  assert.equal(await skin(), 'off');
  await page.click('[role="switch"][aria-label="Show the skin"]');
  assert.equal(await skin(), 'on');

  // The three-line button closes the panel again (and Escape does too, once nothing else is open), and opens it once more.
  await page.click('[data-testid="controls-toggle"]');
  await page.waitForFunction(() => !document.querySelector('[data-testid="body-controls"]'), { timeout: 10_000 });
  await page.click('[data-testid="controls-toggle"]');
  await page.waitForSelector('[data-testid="body-controls"]');
  await page.keyboard.press('Escape');
  await page.waitForFunction(() => !document.querySelector('[data-testid="body-controls"]'), { timeout: 10_000 });
  await page.click('[data-testid="controls-toggle"]');
  await page.waitForSelector('[data-testid="body-controls"]');

  // The female body loads too, and the same muscles are there.
  await clickText('[data-slot="toggle-group-item"]', 'Female');
  if (webgl) {
    await page.waitForFunction(() => document.querySelector('[data-testid="body-viewer"]')?.dataset.loaded === 'female', { timeout: 20_000 });
    await new Promise((resolve) => setTimeout(resolve, 600));
    await shot('body-female');
  }
  await openList();
  assert.ok(!(await text()).includes('Not in the female model'));
  await page.type('input[aria-label="Search muscles"]', 'zzzz');
  await waitForText('No muscle matches.');
});

test('the muscle card: an admin gives a muscle a subtitle and words of their own, and adds, moves, removes and writes its exercises', async () => {
  const exercise = (name, group, extra) => ({ name, muscleGroup: group, equipment: 'Bodyweight', level: 'Beginner', instructions: `How to do ${name}.`, tips: '', defaultSets: 3, defaultReps: 10, defaultWeightKg: 0, videoUrl: '', active: true, ...extra });
  await testEnv.withSecurityRulesDisabled(async (ctx) => { await setDoc(doc(ctx.firestore(), 'library/e-edit-row'), exercise('Edit Row', 'Back', {})); });
  const read = async (where) => { let snap; await testEnv.withSecurityRulesDisabled(async (ctx) => { snap = await getDoc(doc(ctx.firestore(), where)); }); return snap; };
  const seen = (pattern) => page.waitForFunction((source) => new RegExp(source, 'i').test(document.body.innerText), { timeout: 15_000 }, pattern);
  const goToBody = async () => { // (leaving the page and coming back starts it afresh, so what was saved is read from the cloud again)
    await clickText('nav[aria-label="Main"] a', 'Library');
    await waitForText('Exercise library');
    await clickText('nav[aria-label="Main"] a', 'Body');
    await page.waitForSelector('[data-testid="body-stage"]');
  };
  const openList = async () => {
    if (!(await page.$('[data-testid="body-controls"]'))) { await page.click('[data-testid="controls-toggle"]'); await page.waitForSelector('[data-testid="body-controls"]'); }
    if (!(await page.$('[data-testid="muscle-panel"]'))) await page.click('button[aria-label="Open the muscle list"]');
    await page.waitForSelector('[data-muscle="feet"]');
  };
  // (The box slides while the camera flies to the muscle, so wait for that to end before clicking on it.)
  const settle = (ms = 1200) => new Promise((resolve) => setTimeout(resolve, ms));
  const openFeet = async () => { await openList(); await page.click('[data-muscle="feet"]'); await page.waitForSelector('[data-testid="muscle-title"]'); await settle(); };
  const edit = async () => { await page.click('button[aria-label="Edit this muscle\'s info"]'); await page.waitForSelector('form[aria-label="Edit Feet"]'); };
  const fill = async (selector, value) => { await page.$eval(selector, (e) => { e.focus(); e.select(); }); if (value) await page.type(selector, value); else await page.keyboard.press('Backspace'); };

  await goToBody();
  await openFeet();
  assert.equal(await page.$eval('[data-testid="muscle-title"]', (e) => e.textContent), 'Feet');
  assert.ok(!(await page.$('[data-testid="muscle-subtitle"]')), 'a muscle starts with no subtitle');
  const builtIn = (await page.$eval('[data-testid="muscle-callout"] p', (e) => e.textContent));
  assert.ok(builtIn.includes('small muscles of the sole'), 'and the built-in description');

  // A subtitle and a description of their own.
  await edit();
  await fill('input[name="subtitle"]', 'Sole muscles');
  await fill('textarea[name="about"]', 'Our own words about the feet.');
  await shot('muscle-edit');
  await page.click('form[aria-label="Edit Feet"] button[type="submit"]');
  await waitForText('Our own words about the feet.');
  assert.equal(await page.$eval('[data-testid="muscle-subtitle"]', (e) => e.textContent), 'Sole muscles');
  assert.ok(!(await page.$('form[aria-label="Edit Feet"]')), 'the form closes when saved');
  let saved = (await read('muscles/feet')).data();
  assert.equal(saved.subtitle, 'Sole muscles');
  assert.equal(saved.about, 'Our own words about the feet.');
  assert.equal(saved.updatedBy, users.admin);
  assert.ok(Number.isInteger(saved.updatedAt));
  await assertNoLeaks('an edited muscle');

  // Too long: said in the form, and nothing is written.
  await edit();
  await fill('input[name="subtitle"]', 'x'.repeat(70));
  await page.click('form[aria-label="Edit Feet"] button[type="submit"]');
  await waitForText('Keep the subtitle to 60 characters or fewer (it is 70).');
  assert.equal((await read('muscles/feet')).data().subtitle, 'Sole muscles', 'a refused edit changes nothing');
  await clickText('form[aria-label="Edit Feet"] button', 'Cancel');
  assert.ok(!(await page.$('form[aria-label="Edit Feet"]')));

  // What was written is there when the page is opened again, in the list's search and under the hover name, and the exercises count is unchanged.
  await goToBody();
  await openList();
  await page.type('input[aria-label="Search muscles"]', 'sole muscles');
  assert.deepEqual(await page.$$eval('[data-muscle]', (buttons) => buttons.map((b) => b.dataset.muscle)), ['feet'], 'the subtitle can be searched for');
  await page.click('[data-muscle="feet"]');
  await page.waitForSelector('[data-testid="muscle-subtitle"]');
  await settle();
  assert.equal(await page.$eval('[data-testid="muscle-subtitle"]', (e) => e.textContent), 'Sole muscles');
  assert.ok((await text()).includes('Our own words about the feet.'), 'the description they wrote replaces the built-in one');
  await shot('muscle-subtitle');

  // Back to the built-in text.
  await edit();
  await clickText('form[aria-label="Edit Feet"] button', 'Reset to built-in');
  await page.waitForFunction(() => !document.querySelector('[data-testid="muscle-subtitle"]'), { timeout: 15_000 });
  assert.ok((await text()).includes('small muscles of the sole'), 'the built-in description is back');
  assert.ok(!(await read('muscles/feet')).exists(), 'and nothing is kept about the muscle');
  // Writing the built-in text again is not an override either.
  await edit();
  await fill('input[name="subtitle"]', '');
  await clickText('form[aria-label="Edit Feet"] button', 'Save');
  await page.waitForFunction(() => !document.querySelector('form[aria-label="Edit Feet"]'), { timeout: 15_000 });
  assert.ok(!(await read('muscles/feet')).exists(), 'saving it unchanged writes nothing');

  // Exercises: none is set to train the feet to begin with; add one, make it a helper, take it off.
  await page.waitForFunction(() => [...document.querySelectorAll('button')].some((b) => /^Show exercises \(\d+\)$/.test(b.innerText.trim())), { timeout: 15_000 });
  await page.evaluate(() => [...document.querySelectorAll('button')].find((b) => /^Show exercises \(\d+\)$/.test(b.innerText.trim()))?.click());
  await page.click('button[aria-label="Add an exercise to this muscle"]');
  await page.waitForSelector('[data-testid="exercise-picker"]');
  await page.type('input[aria-label="Search the library"]', 'Edit Row');
  await page.click('button[aria-label="Add Edit Row as a main exercise"]');
  await seen('Main exercises · 1');
  assert.deepEqual((await read('library/e-edit-row')).data().primaryMuscles, ['feet']);
  const open = () => page.evaluate(() => [...document.querySelectorAll('[data-testid="muscle-exercises"] button')].find((b) => b.innerText.startsWith('Edit Row'))?.click());
  await open();
  await waitForText('Make it a helper');
  await settle(400); // (the accordion is still sliding open)
  await clickText('button', 'Make it a helper');
  await seen('Also works this muscle · 1');
  let row = (await read('library/e-edit-row')).data();
  assert.deepEqual([row.primaryMuscles, row.secondaryMuscles], [[], ['feet']], 'it moves from the main exercises to the helpers, not into both');
  await open();
  await waitForText('Make it a main exercise');
  await settle(400);
  await page.click('button[aria-label="Remove Edit Row from Feet"]');
  await page.waitForFunction(() => !/Also works this muscle/i.test(document.body.innerText), { timeout: 15_000 });
  row = (await read('library/e-edit-row')).data();
  assert.deepEqual([row.primaryMuscles, row.secondaryMuscles], [[], []], 'taken off the muscle');
  assert.equal(row.name, 'Edit Row', 'and nothing else of the exercise changed');

  // A new exercise written for the muscle starts out training it.
  await page.click('button[aria-label="Add an exercise to this muscle"]'); // (closes the picker)
  await clickText('button', 'New exercise');
  await page.waitForSelector('[role="dialog"] input[name="name"]');
  assert.equal(await page.$eval('[role="dialog"] select[name="muscleGroup"]', (e) => e.value), 'Full body', 'in the muscle\'s own library group');
  assert.ok(await page.evaluate(() => [...document.querySelectorAll('[role="dialog"] button')].some((b) => b.innerText.includes('Add to library'))), 'and the form says it adds to the library');
  await page.type('[role="dialog"] input[name="name"]', 'Toe Yoga');
  await clickText('[role="dialog"] button', 'Add to library');
  await waitForNoDialog();
  await seen('Main exercises · 1');
  assert.ok((await text()).includes('Toe Yoga'));
  const fresh = (await read('library/toe-yoga')).data();
  assert.deepEqual(fresh.primaryMuscles, ['feet']);
  assert.equal(fresh.updatedBy, users.admin);
  await shot('muscle-exercises');
  await assertNoLeaks('the muscle card');
});

test('the team page lists the trainers and adds another', async () => {
  await clickText('nav[aria-label="Main"] a', 'Team');
  await waitForText('admin@example.com (you)');
  await page.type('input[name="uid"]', users.alice);
  await page.type('input[name="email"]', 'alice@example.com');
  await clickText('button', 'Add to team');
  await waitForText('Added to the team');
  await waitForText('alice@example.com');
  assert.ok(!(await page.evaluate(() => [...document.querySelectorAll('tbody tr')].find((tr) => tr.innerText.includes('(you)')).innerText.includes('Remove'))), 'you cannot remove yourself');
});

test('signing out returns to the sign-in form', async () => {
  await page.click('button[aria-label="Account menu"]');
  await clickText('[role="menuitem"]', 'Sign out');
  await waitForText('Sign in with an admin account');
});

test('the browser reported no errors and no policy violations', () => {
  const real = problems.filter((p) => !/Failed to load resource: the server responded with a status of (400|403|404)/.test(p));
  assert.deepEqual(real, [], 'the browser complained:\n' + real.join('\n'));
});
