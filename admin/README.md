# Strivo Coach Dashboard

A web dashboard for the **gym owner or trainer**. The people who use the Strivo app are their **clients**; the dashboard
shows how each one is doing, so the trainer can coach them: who came to train and who did not, how the lifts and body
weight are developing, and how much they eat. It also manages the exercise library that clients browse in the app.

It is a Next.js app (React, TypeScript, shadcn/ui) exported as a static site and hosted on Firebase Hosting; it talks
straight to the same Firebase project as the app, so there is no server to run.

| Page | What it is for |
|---|---|
| **Overview** | Clients, who trained in the last 7 days, workouts per day, **who needs a nudge**, latest workouts, most workouts |
| **Clients** | Every client: training status (on track, slipping, missing), last workout, this week against their plan, body weight and its change, average calories and protein. Filter, sort (needs a nudge first), search, page |
| **Attendance** | Every client's last 14 days as a row of squares, this week against the plan, last workout. Filter by missed this week, below plan, met the plan |
| **A client** | Attendance (12 weeks and a calendar), **strength** (the heaviest set of every exercise, workout by workout, with the change since the first), body weight, **daily calories and protein**, finished workouts with every set, activity, their plans, and **Send a plan** (pick a day, start from one of their plans or the library, add a note) |
| **Activity** | What all clients added or changed, newest first |
| **Body** | A 3D body (male or female) with a see-through **skin layer over the muscles**: hover a muscle to light it up, **click it and the skin fades away**, the muscle glows and the rest step back. Read what it does and press **Show exercises** for the library exercises that train it. "Fade surface" shows the deep muscles, and there are switches for the skin and the skeleton. Every muscle is also in a list (the way to reach one with the keyboard) |
| **Library** | Add, edit, hide and delete library exercises, and set the **main and helping muscles** each one trains (what the Body page lists); import the starter library, and **fill in muscles** on exercises that predate the muscle fields |
| **Team** | The trainers and owners who can use the dashboard |

## What a trainer sees, and what stays private

| The trainer sees | The trainer never sees |
|---|---|
| Workouts: exercises, sets, weights and reps | **What a client eats.** The food log is not readable by an admin: `firestore.rules` has no rule for it |
| Plans, body weight, profile (age, gender, height, rest days) | The names of foods, anywhere (older activity lines that named one are rewritten by the app, and shown without it here) |
| Attendance and how it compares with the plan | The client's exercise history |
| **Daily calorie and protein totals** (also carbs, fat, number of meals) | |

A trainer **reads** client data but cannot change it (the rules enforce this, not just the page). The only things a
trainer writes are library exercises, the team list, and **plans sent to a client** (`users/{uid}/assigned`). A sent plan
is not the client's plan itself: the client's app adds it to their plans (on the day picked) the next time it syncs, and
marks it delivered. Until then the trainer can withdraw it; after that it belongs to the client, who can change or delete it.

## One-time setup

Needs Node 20.9 or newer (22 to run the tests) and the Firebase CLI (`npm install -g firebase-tools`).

1. **Publish the rules and indexes** (they hold the admin rules, the library, and what a trainer may read). Do this again whenever `firestore.rules` or `firestore.indexes.json` change; the index for the attendance page takes a few minutes to build:

   ```
   firebase login
   firebase use <your-project-id>       # or: firebase use --add
   firebase deploy --only firestore
   ```

2. **Add a Web app** in the Firebase console (Project settings → Your apps → `</>`). Copy `.env.example` to
   `.env.local` in this folder and fill in the four `NEXT_PUBLIC_FIREBASE_*` values from the web config.
   `.env.local` is git-ignored. (These values are not secrets, they ship in every web page; the rules and the admin
   list are what protect the data.)

3. **Create the first admin.** Sign up in the Strivo app (or add a user under Authentication) and copy the account's
   **User UID**. In the console go to Firestore Database → *Start collection* → collection id `admins` → document id =
   that UID → add a field `email` (string). From then on, admins can add each other on the Admins page.

4. **Build and deploy the dashboard:**

   ```
   cd admin
   npm install
   npm run build                        # writes the static site to admin/out
   cd ..
   firebase deploy --only hosting
   ```

   It is served at `https://<your-project-id>.web.app`. In Authentication → Settings → Authorized domains, make sure that
   domain is listed (it is by default). The settings are baked in when you build, so build again after changing
   `.env.local`.

5. **Fill the library:** open the Library page and press **Import starter library** (74 exercises). The app already
   ships with the same exercises, so users have a library even before this step; once the cloud has one, it replaces the
   built-in copy on their phones.

## Working on it

```
cd admin
npm install
npm run dev          # http://localhost:3000, uses the project in .env.local
npm run typecheck    # TypeScript
npm run lint         # ESLint
npm test             # the dashboard's logic (Vitest, no Firebase needed)
npm run build        # the static site, in admin/out
```

To try it against the Firebase emulators with no real data, run the browser tests (`tools/emulator-tests`), which start
the emulators, seed users and activity, build the dashboard into `admin/.test-build` and drive it in a real browser.

## How it is laid out

```
src/app/         the pages (thin: each renders a view and sets the title) and global styles
src/views/       what each page shows: Overview, Clients, Attendance, ClientDetail (+ tabs), Activity, Library (+ form), Team
src/components/  sign-in gate, sidebar frame, dialogs and messages, activity list, small UI pieces
src/components/ui/  the shadcn/ui components (Tailwind CSS v4 + Base UI), used as they come
src/lib/         logic.ts and coaching.ts (pure, tested), paging.ts, data.ts (every Firestore read and write), firebase.ts, hooks, types
seed/library.json  the 74 starter exercises (the Android app bundles the same file)
```

The look (black background, amber accent) is set once, as CSS variables in `src/app/globals.css`; the shadcn components pick
it up from there. To add another component: `npx shadcn@latest add <name>` (see `components.json`).

A client's page is `/clients/view/?uid=...` rather than `/clients/<uid>`, because a static site cannot have a page for every
account id in advance.

## What it relies on

- **Client summary**: `users/{uid}` holds the name, email, last active and app version, and `stats`: workouts in the last 7
  and 30 days, the last workout, weekdays with a plan, body weight and its change over 30 days, and the average calories
  and protein of the last week. The app writes it at most every five minutes while it syncs. A client who has not
  opened a version of the app that does this shows as "No data yet" until they do.
- **Daily intake**: `users/{uid}/nutrition/{date}`, written by the app from the food log: calories, protein, carbs, fat and
  the number of meals. Never the foods.
- **Workouts, plans, body weight**: `users/{uid}/sessions`, `plans`, `exercises`, `weights` and `meta/profile`, which the app
  already backs up. Demo data (debug builds) is left out everywhere.
- **Activity**: the app writes one line per thing a client does to `users/{uid}/events` (see `ActivityLog.kt`).
- **Library**: `library/{exerciseId}`. The app copies it to the phone and shows only exercises that are *shown*.
- **Sent plans**: `users/{uid}/assigned/{id}`: the plan name, day (a weekday, or `Prebuilt` for the client's plan
  library), a note, the trainer's name, up to 30 exercises (name, sets, reps, weightKg, dropSets, notes), and a status:
  `sent` until the app picks it up, then `delivered` with `deliveredAt`. Only a trainer creates or withdraws one; only
  the client's app marks it delivered (`firestore.rules`). A client needs an app version that knows about sent plans.
- **Security**: `firestore.rules` at the project root. Text from clients is only ever displayed as text (nothing in the
  dashboard uses `dangerouslySetInnerHTML`), and the hosting headers (`firebase.json`) set a content security policy
  that only allows scripts from this site. Next's static export starts the page with small inline scripts, so the policy
  has to allow `'unsafe-inline'` for scripts; that is why the "text only" rule above matters.

## Privacy

A trainer can see how a client trains, their body weight, and how many calories and how much protein they eat, but not
what they eat. Tell clients: the app shows a note in Profile saying so, and your privacy policy should say the same (and
that this data is stored in your Firebase project and visible to the trainer and owner for coaching). Keep the team list
short, and remove people who no longer need access.

## Tests

```
cd admin && npm test                 # the dashboard's logic (no Firebase needed)
cd tools/emulator-tests && npm test  # the security rules, and the dashboard in a real browser, on the emulators
```

The browser tests build the dashboard first. On a machine short of memory, build it yourself once (`ADMIN_BUILD_DIR=.test-build` and the
`NEXT_PUBLIC_FIREBASE_*` values from `dashboard.test.js`, from `admin/`) and run the tests with `SKIP_BUILD=1`. The 3D body is checked in
the real scene when the test browser has WebGL (it uses a software renderer); without it the page says so and the list is tested.

## The 3D body

The models are not in `admin/`: they are `models/anatomy` at the repository root (see its README), copied into `public/models/anatomy` by
`npm run dev` and `npm run build` (that copy is not committed). The page loads `muscles.json` (the muscle catalogue: names, descriptions, where
each muscle is) and the chosen `.glb`; `src/lib/bodyScene.ts` is the three.js scene (turning, hover and click by drawing the
muscles in unique colours into a 1-pixel buffer, which looks straight through the skin; the camera swinging round the body to a muscle; the skin fading),
`src/lib/muscles.ts` the pure rules (which exercises train a muscle, where the camera goes and how it travels, how a selected, hovered or
faded muscle is drawn), tested in `muscles.test.ts`. An exercise says which muscles it trains with `primaryMuscles` and `secondaryMuscles` (muscle ids from
the catalogue, such as `delt-rear`); one without them is listed under "More ... exercises" for its broad group.
The credit for the models is shown on the page and required by their licence (CC BY-SA 4.0).
