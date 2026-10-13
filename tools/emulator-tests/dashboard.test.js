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
const { doc, setDoc, getDocs, collection } = require('firebase/firestore');

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

const TYPES = { '.html': 'text/html', '.js': 'text/javascript', '.css': 'text/css', '.json': 'application/json', '.txt': 'text/plain', '.svg': 'image/svg+xml', '.ico': 'image/x-icon' };

/** Builds the dashboard pointing at the emulators (the settings are baked in at build time). */
function buildDashboard() {
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

async function seed() {
  users.admin = await signUp('admin@example.com', 'admin-pass-1');
  users.alice = await signUp('alice@example.com', 'alice-pass-1');
  users.bob = await signUp('bob@example.com', 'bob-pass-1');
  users.outsider = await signUp('outsider@example.com', 'outsider-pass-1');
  testEnv = await initializeTestEnvironment({ projectId: 'strivo-test', firestore: { rules: fs.readFileSync(path.join(__dirname, '../../firestore.rules'), 'utf8') } });
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    const put = (p, data) => setDoc(doc(db, p), data);
    await put(`admins/${users.admin}`, { email: 'admin@example.com', addedAt: NOW });
    await put(`users/${users.alice}`, { email: 'alice@example.com', name: 'Alice Active', lastActiveAt: NOW - 3 * MIN, createdAt: NOW - 20 * 86_400_000, appVersion: '1.0.0', platform: 'android', lastEvent: 'Finished "Push Day": 2 exercises, 960 kg, 42 min', lastEventAt: NOW - 3 * MIN });
    await put(`users/${users.bob}`, { email: 'bob@example.com', name: 'Bob Inactive', lastActiveAt: NOW - 30 * 86_400_000, createdAt: NOW - 90 * 86_400_000, appVersion: '1.0.0', platform: 'android' });
    const a = `users/${users.alice}`;
    await put(`${a}/events/1`, { id: 1, at: NOW - 3 * MIN, type: 'workout_finished', summary: 'Finished "Push Day": 2 exercises, 960 kg, 42 min' });
    await put(`${a}/events/2`, { id: 2, at: NOW - 50 * MIN, type: 'exercise_added', summary: 'Added "Bench Press" to Push Day (Monday) · 4 × 8 @ 60 kg' });
    await put(`${a}/events/3`, { id: 3, at: NOW - 2 * 86_400_000, type: 'plan_created', summary: 'Created plan "Push Day" for Monday' });
    await put(`${a}/events/4`, { id: 4, at: NOW - 3 * 86_400_000, type: 'food_logged', summary: 'Logged "<img src=x onerror=window.__xss=1>Dal" (560 kcal) as dinner' });
    await put(`users/${users.bob}/events/1`, { id: 1, at: NOW - 30 * 86_400_000, type: 'weight_logged', summary: 'Weighed in at 82 kg' });
    await put(`${a}/sessions/1`, {
      id: 1, planName: 'Push Day', date: '2026-10-12T07:30:00', totalTime: '00:42:14', isDemo: 0,
      exercises: JSON.stringify([
        { name: 'Bench Press', sets: [{ weight: 30, reps: 10, isCompleted: true, type: 'warmup' }, { weight: 60, reps: 8, isCompleted: true }, { weight: 60, reps: 8, isCompleted: true }] },
        { name: 'Push-Up', sets: [{ weight: 0, reps: 15, isCompleted: true }] },
      ]),
    });
    await put(`${a}/plans/1`, { planId: 1, planName: 'Push Day', planDay: 'Monday', isDemo: 0 });
    await put(`${a}/plans/2`, { planId: 2, planName: 'Push Day', planDay: 'Prebuilt', isDemo: 0 });
    await put(`${a}/exercises/1`, { id: 1, planId: 1, name: 'Bench Press', weight: '60.0', sets: '4', reps: '8', notes: 'Keep wrists straight', dropSets: 1, sortOrder: 0 });
    await put(`${a}/exercises/2`, { id: 2, planId: 1, name: 'Push-Up', weight: '0.0', sets: '3', reps: '15', notes: '', dropSets: 0, sortOrder: 1 });
    await put(`${a}/weights/2026-10-01`, { date: '2026-10-01', weight: 72.5 });
    await put(`${a}/weights/2026-10-08`, { date: '2026-10-08', weight: 71.8 });
    await put(`${a}/weights/2026-10-12`, { date: '2026-10-12', weight: 71.0 });
    await put(`${a}/food/1`, { id: 1, date: '2026-10-12', meal: 'Dinner', name: 'Dal and rice', calories: 560, isDemo: 0 });
    await put(`${a}/food/2`, { id: 2, date: '2026-10-12', meal: 'Lunch', name: 'Chicken bowl', calories: 620, isDemo: 1 });
    await put(`${a}/meta/profile`, { gender: 'Female', age: 29, height: 168.0, weight: 71.0, restDays: ['SUNDAY'], updatedAt: 1 });
    const exercise = (id, name, group) => ({ name, muscleGroup: group, equipment: 'Barbell', level: 'Beginner', instructions: 'Do it carefully and with control.', tips: '', defaultSets: 3, defaultReps: 10, defaultWeightKg: 20, videoUrl: '', active: true, createdAt: NOW, updatedAt: NOW });
    await put('library/bench-press', exercise('bench-press', 'Bench Press', 'Chest'));
    await put('library/old-move', { ...exercise('old-move', 'Old Move', 'Legs'), active: false });
  });
}

// ---- Small helpers for driving the page ----

/** With SCREENSHOTS=<folder> the test saves a picture of each main page, for looking at the design. */
const shot = async (name) => { if (process.env.SCREENSHOTS) await page.screenshot({ path: path.join(process.env.SCREENSHOTS, name + '.png') }); };

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
const waitForGone = (t, timeout = 15_000) => page.waitForFunction((s) => !document.body.innerText.includes(s), { timeout }, t);

/** Clicks the first visible element of a kind whose text matches. */
async function clickText(selector, label) {
  const handle = await page.waitForFunction((sel, l) => [...document.querySelectorAll(sel)].find((e) => e.innerText.trim() === l || e.textContent.trim() === l), { timeout: 15_000 }, selector, label);
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
  browser = await puppeteer.launch({ executablePath: CHROME, headless: true, args: ['--no-sandbox', '--disable-gpu'] });
  page = await browser.newPage();
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

// ---- The tests, in the order an admin would use the dashboard ----

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

test('an admin signs in and sees the overview', async () => {
  await page.click('input[name="password"]', { clickCount: 3 });
  await page.type('input[name="password"]', 'admin-pass-1');
  await clickText('button', 'Sign in');
  await waitForText('Recent activity');
  const body = await text();
  assert.match(body, /Users\s*\n?\s*2|2\s*\n?\s*Users/);
  assert.ok(body.includes('Alice Active'), 'the recent activity names the user');
  assert.ok(body.includes('Finished "Push Day"'));
  await assertNoLeaks('the overview');
  await shot('overview');
});

test('the users page lists everyone and searches', async () => {
  await clickText('.nav a', 'Users');
  await waitForText('loaded ·'); // only on the users page
  const body = await text();
  assert.ok(body.includes('Alice Active') && body.includes('alice@example.com'));
  assert.ok(body.includes('Online now'), 'Alice was active 3 minutes ago');
  assert.ok(body.includes('Inactive'), 'Bob was last active 30 days ago');
  await page.type('input[type="search"]', 'bob');
  await waitForGone('Alice Active');
  await page.click('input[type="search"]', { clickCount: 3 });
  await page.keyboard.press('Backspace');
  await waitForText('Alice Active');
  await assertNoLeaks('the users page');
  await shot('users');
});

test('a user page shows their activity, workouts, plans, body and food', async () => {
  await clickText('a.user-cell', 'Alice Active\nalice@example.com');
  await waitForText('Added "Bench Press" to Push Day (Monday) · 4 × 8 @ 60 kg');
  await waitForText('workouts');
  let body = await text();
  assert.ok(body.includes('Created plan "Push Day" for Monday'));
  assert.match(body, /today/i, 'events are grouped by day');

  // Activity written by a user is shown as text, never run as HTML.
  assert.ok(body.includes('<img src=x onerror=window.__xss=1>Dal'), 'the markup is displayed as plain text');
  assert.equal(await page.evaluate(() => window.__xss), undefined, 'the script in that text never ran');
  assert.equal(await page.evaluate(() => document.querySelectorAll('img[src="x"]').length), 0);

  await assertNoLeaks('the user activity tab');
  await shot('user-activity');
  await clickText('button.tab', 'Workouts');
  await waitForText('Push Day');
  await clickText('summary', await page.evaluate(() => document.querySelector('details.card > summary').innerText));
  await waitForText('W 30 × 10, 60 × 8, 60 × 8');
  body = await text();
  assert.ok(body.includes('960 kg'), 'warm-ups are left out of the volume');
  assert.ok(body.includes('42 min'));

  await clickText('button.tab', 'Plans');
  await waitForText('Keep wrists straight');
  body = await text();
  assert.ok(body.includes('4 × 8 + 1 drop @ 60.0 kg') || body.includes('4 × 8 + 1 drop @ 60 kg'));
  assert.ok(body.includes('Library'), 'the library copy of the plan is labelled');

  await clickText('button.tab', 'Body');
  await waitForText('Weight over time');
  await assertNoLeaks('the body tab');
  await shot('user-body');
  body = await text();
  assert.ok(body.includes('Female') && body.includes('168 cm'));
  assert.ok(body.includes('72.5 kg') && body.includes('71 kg'));
  assert.equal(await page.evaluate(() => document.querySelectorAll('svg.sparkline polyline').length), 1);

  await clickText('button.tab', 'Food');
  await waitForText('1,180 kcal'); // the day adds up
  await page.click('details.card > summary');
  await waitForText('Dal and rice');
  body = await text();
  assert.ok(body.includes('demo data'), 'demo rows are marked');
  await shot('user-food');
});

test('a user page opens straight from its address, and says so when no user is chosen', async () => {
  await page.goto(`${baseUrl}/users/view/?uid=${users.alice}`);
  await waitForText('Alice Active');
  await waitForText('Added "Bench Press" to Push Day (Monday) · 4 × 8 @ 60 kg');
  await assertNoLeaks('a user page opened by address');

  await page.goto(`${baseUrl}/users/view/?uid=nobody-here`);
  await waitForText('That user has no summary yet');

  await page.goto(`${baseUrl}/users/view/`);
  await waitForText('Pick a user from the Users page.');
});

test('the activity page shows everyone, filterable by kind', async () => {
  await clickText('.nav a', 'Activity');
  await waitForText('Everything users add or change');
  await waitForText('Weighed in at 82 kg');
  let body = await text();
  assert.ok(body.includes('Bob Inactive') && body.includes('Alice Active'));
  await clickText('button.chip', 'Weight');
  await waitForGone('Created plan "Push Day"');
  body = await text();
  assert.ok(body.includes('Weighed in at 82 kg'));
});

test('the library: add, validate, hide, import the starter set and delete', async () => {
  await clickText('.nav a', 'Library');
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
  await (await (await row('Tester Curl')).asElement().$$('button'))[1].click();
  await waitForText('Tester Curl is now hidden from users');
  await waitForText('1 shown to users').catch(() => {});

  // The starter library is added without touching what is already there (Bench Press exists, and keeps its id).
  await clickText('button', 'Import starter library');
  await waitForText(`Add ${SEED.length - 1} starter exercises?`);
  await clickText('.modal button', 'Import');
  await waitForText(`${SEED.length - 1} exercises imported`);
  await waitForText(`${SEED.length + 2} of ${SEED.length + 2} exercises`);

  // Deleting asks first.
  await (await (await row('Old Move')).asElement().$$('button'))[2].click();
  await waitForText('Delete "Old Move" from the library?');
  await clickText('.modal button', 'Delete');
  await waitForText('Old Move deleted');
  await waitForText(`${SEED.length + 1} of ${SEED.length + 1} exercises`);
});

test('the admins page lists admins and adds another', async () => {
  await clickText('.nav a', 'Admins');
  await waitForText('admin@example.com (you)');
  await page.type('input[name="uid"]', users.alice);
  await page.type('input[name="email"]', 'alice@example.com');
  await clickText('button', 'Add admin');
  await waitForText('Admin added');
  await waitForText('alice@example.com');
  assert.ok(!(await page.evaluate(() => [...document.querySelectorAll('tbody tr')].find((tr) => tr.innerText.includes('(you)')).innerText.includes('Remove'))), 'you cannot remove yourself');
});

test('signing out returns to the sign-in form', async () => {
  await clickText('button', 'Sign out');
  await waitForText('Sign in with an admin account');
});

test('the browser reported no errors and no policy violations', () => {
  const real = problems.filter((p) => !/Failed to load resource: the server responded with a status of (400|403|404)/.test(p));
  assert.deepEqual(real, [], 'the browser complained:\n' + real.join('\n'));
});
