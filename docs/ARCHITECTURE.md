# Architecture

Strivo is a single-module Android app (Kotlin, Jetpack Compose, Material 3). The code is organised by **layer** first
and by **feature** second, and one rule runs through all of it: **dependencies only point downwards**.
`ArchitectureTest` fails the build when a wrong import is written, so this document and the code cannot drift apart.

```
app/src/main/java/com/example/strivo/
├── MainActivity.kt, StrivoApp.kt     entry points; StrivoApp wires the data layer once per signed-in account
├── navigation/                       the nav graph, routes and the bottom bar; the only place that knows every screen
├── feature/                          one package per screen area: screens, view models and their parts
│   ├── home/  workout/  plans/  analytics/  food/  history/  profile/  auth/  coach/  body/
├── core/                             shared by every feature, knows nothing about features or storage
│   ├── ui/theme/                       colours, type, shapes (AppColors, AppType, AppShapes)
│   ├── ui/components/                  buttons, cards, dialogs, pickers, top bar ...
│   └── util/                           time and number formatting
├── domain/                           the app's own rules, plain Kotlin with no Android in it
│   ├── model/                          Exercise, Plan, ExerciseSet, SetType, WorkoutSession
│   ├── analytics/                      volume, consistency, body metrics, nutrition, per-exercise progress
│   ├── coach/                          the advice the coach gives, and the calorie targets
│   └── plan/                           whether a plan counts as done today
└── data/                             where things are kept
    ├── local/db/                       SQLite: Schema (tables, migrations), StrivoDatabase (queries), RowMapping
    ├── local/prefs/                    SharedPreferences: session and profile
    ├── remote/                         Firebase Authentication
    ├── sync/                           Firestore backup and restore (SyncManager) and its pure rules (SyncPlanner)
    ├── repository/                     StrivoRepository: the one door the view models use for data
    ├── share/                          the text format used to share plans
    └── demo/                           made-up data for trying the app (debug builds)
```

## The dependency rule

```
navigation ──▶ feature ──▶ core ──▶ domain
                  │                    ▲
                  └──────▶ data ───────┘
```

| Layer | May use | Must not use |
|---|---|---|
| `domain` | itself and the Kotlin/Java standard library | Android, `data`, `core`, `feature`, `navigation` |
| `core` | `domain` | `data`, `feature`, `navigation` |
| `data` | `domain`, `core.util` | `feature`, `navigation`, `core.ui` |
| `feature` | `domain`, `core`, `data` (through its view model) | `navigation` |
| `navigation` | everything | |

Inside `data`, storage and the network (`local`, `remote`) sit below `sync`, which sits below `repository`; nothing points
back up. Between features only these links are allowed, because the screens really do open each other:
`home → plans, profile, auth, coach, body`, `analytics → coach`, `history → plans`, `profile → auth`.

## How a screen works

```
Composable screen ──▶ ViewModel ──▶ StrivoRepository ──▶ StrivoDatabase / UserPrefs / SyncManager
      ▲                   │
      └──── StateFlow ────┘
```

- A **screen** draws state and reports taps. It holds only what the user is doing right now (a sheet that is open, a
  field being typed in).
- A **view model** turns that into state (`StateFlow`) and calls the repository. Work is done in `viewModelScope`.
- The **repository** is the one place that combines the database, preferences and sync. Everything on the phone is
  written to SQLite first, so the app works offline; `sync/` then copies changes to Firestore and reads them back.
- **Domain code is pure functions** over plain data classes: `buildReport(sessions, range, today)`,
  `progressionAdvice(history, today)`, `isPlanDoneToday(...)`. They take `today` as an argument instead of reading the
  clock, so every rule can be tested with a fixed date.

## Conventions

- **A package is a feature or a layer, and its folder says so.** The package of a file always matches its folder
  (checked by the architecture test).
- **Files are named by what they show** (`ExerciseCard.kt`, `WeekStrip.kt`), not `Utils` or `Helpers`. A file holds one
  screen or a few small things that belong together. No file may pass 600 lines; when one does, split it by what it
  shows. Inside a feature package, pieces of a screen are `internal`; a screen's entry point is public.
- **Shared UI goes to `core/ui/components`** as soon as a second feature needs it, not before. Features never import
  another feature's UI helpers (only the links listed above).
- **Numbers shown to the user are formatted in one place** (`core/util/Format.kt`), so "62.5 kg" looks the same everywhere.
- **Tests mirror the packages.** `domain/analytics/ExerciseProgress.kt` is tested in
  `src/test/.../domain/analytics/ExerciseProgressTest.kt`. Domain and sync rules have plain JVM tests; screens are
  checked by hand on a device or emulator.
- **Text files are UTF-8 without a BOM, with LF line endings** (see `.editorconfig`).

## Adding a feature

1. Make `feature/<name>/` with `<Name>Screen.kt` and `<Name>ViewModel.kt`.
2. Put any rule that is not about drawing in `domain/` first (as a pure function) and test it.
3. Add the data it needs to `StrivoRepository`, and to `StrivoDatabase` with a step in `Schema.upgrade` if a table
   changes (bump `Schema.VERSION`).
4. Register the route in `navigation/Routes.kt` and the screen in `navigation/StrivoNavGraph.kt`.
5. Run `./gradlew :app:testDebugUnitTest`. If it fails with an `ArchitectureTest` message, an import points the wrong way.

## Data and sync

- Plans, exercises, workout sessions, exercise history, food, extra activity and weigh-ins live in SQLite, one database
  file per account (`strivo_<uid>.db`). `Schema.kt` creates the tables and holds one migration step per version.
- SQLite triggers write every change to `sync_log`. `SyncManager` uploads that log to Firestore under `users/{uid}`,
  then reads the cloud back. A change made on the phone and not yet uploaded is never overwritten.
- Sets are stored as JSON inside their exercise or workout session. New fields (like a set's `type`) are optional in the
  JSON, so older data keeps loading.

## Activity log, library and the coach dashboard

- **Who sees what.** The dashboard is for the gym owner or trainer (an admin); the app's users are their clients. A trainer
  sees what coaching needs: workouts and the weights lifted, plans, body weight, profile, attendance, and the *daily calorie
  and protein totals*. A trainer never sees what a client eats: the food log (`users/{uid}/food`) and the exercise history
  are not readable by an admin (`firestore.rules`), and nothing the dashboard reads contains a food's name.
- **Activity log.** `domain/activity/ActivityLog.kt` turns each user action into one plain line ("Added "Bench Press" to Push
  Day (Monday) · 4 × 8 @ 60 kg"). `StrivoRepository` records them as it saves (plans, exercises, finished workouts, food,
  weigh-ins, profile). They are stored in `activity_events`, which is a synced table, so they reach `users/{uid}/events`.
  Demo data and things restored from the cloud are not recorded. A food line says the meal and the calories ("Logged dinner
  (560 kcal, 32 g protein)"), never the food; the schema upgrade to version 17 rewrites older lines that named it.
- **Client summary.** `SyncManager.publishUserSummary` keeps `users/{uid}` (name, email, last active, app version, last
  action) up to date, at most every five minutes, together with `stats` (`domain/activity/ClientStats.kt`): workouts in the
  last 7 and 30 days, the last workout, weekdays with a plan, body weight and its change over 30 days, and the average
  calories and protein of the last week. Built from real data only (demo rows are left out). A trainer's client list reads
  just these documents.
- **Daily intake.** The same publish writes one document per day to `users/{uid}/nutrition/{date}` (calories, protein,
  carbs, fat, number of meals) for the last 90 days; only days that changed are written. This is the only thing from the
  food log that reaches the trainer.
- **Plans from the trainer.** A trainer sends a plan from the dashboard to `users/{uid}/assigned/{id}` (status `sent`), one per
  day, so a week is seven of them. They are **offers, not plans**: `SyncManager.pullAssignedPlans` reads the ones still waiting
  (`sent` or `delivered`) on each pull, keeps them in `UserPrefs` and marks each `delivered` (it reached the phone). Plans from
  one trainer sent within an hour of each other are grouped (`groupOffers`, `domain/plan/PlanAssignment.kt`), so Home shows ONE
  "From your trainer" card for a week (`feature/home/TrainerOffersCard.kt`): "Coach sent you 7 plans, Mon to Sun", with
  **Accept** and **Decline**. Accept (`StrivoRepository.acceptOffer`) adds every plan to the day the trainer chose
  (`StrivoDatabase.importPlan`) and remembers the ids so none is added twice; Decline adds nothing and remembers the ids so they
  never come back. Either writes one `plan_assigned` line to the activity log, and the next sync tells the cloud
  (`accepted` or `declined` with `answeredAt`; `SyncManager.sendPlanAnswers`), which the trainer sees on the client's Plans tab.
  An accepted plan becomes one of the user's own plans (a "prebuilt" plan, marked `origin: trainer:<id>`) and, when the trainer chose a weekday,
  is put on that day too (see "Plans on a day" below); from then on the user can change or delete it.
- **Plan library and the user's own plans.** `planLibrary/{planId}` in the cloud is pulled into the local `library_plans` table
  (`SyncManager.pullPlanLibrary`, pull-only; a new install starts from `assets/plan_library.json`, the same file as
  `admin/seed/plan_library.json`). The Library screen (the book icon on Home) has an Exercises and a Plans side, so the plan library is browsed where the exercise library is, and a plan is put on a day from there. The Add plan screen lists the user's own plans first (made by them, accepted from a trainer, or taken from
  the library) and the plan library under them; it works with no trainer at all. The old copy-and-paste sharing of plans is gone: the libraries
  replace it.
- **Notifications.** `core/notify`: `Notifier` shows a plan from the trainer (one notification for a week's worth) and the daily workout reminder, each
  on its own channel; nothing is shown without the user's permission (asked once on Home, with the reasons first, and switchable in Profile →
  Notifications, where the reminder time is chosen too). `NotificationWork` (WorkManager, so it survives a restart of the phone) runs an hourly check
  that syncs and tells about each new plan once (`SyncManager.onNewOffers`, `UserPrefs.notifiedOfferIds`; not while the app is on screen), and a
  one-time job each day that decides the reminder (`domain/notify/Reminders.kt`, tested): today's workout still to do, a plan from the trainer waiting,
  or a weigh-in a week old, and nothing when there is nothing to say. Each run sets up the next one.
- **Closed for today.** A card the user closes (a message from the trainer, a coach tip) is hidden for the rest of the day and comes back the next
  one if it is still true (`UserPrefs.snoozeToday`: keys kept with their date, so a new day starts with none).
- **During a workout** every exercise has an (i) button, in the list and in the full-screen set view, that opens everything about it: the plan, its
  notes, how it is done, the tip, the muscles and a video (`ExerciseInfoSheet`; the words come from the exercise library, found by name).
- **Plans on a day.** Putting a plan on a day (`StrivoRepository.copyPlanToDay`, `addLibraryPlanToDay`) makes a copy that stays linked to the user's
  plan (`plans.sourcePlanId`, `exercises.sourceId`; taking a library plan first makes the user's own plan, once). Everything a day plans (every
  set's weight and reps, the sets, name, notes, an exercise added or taken out) is written to the user's plan at once
  (`keepUsersPlanInStep`, `addToUsersPlan`, `removeFromUsersPlan`), and each morning's daily reset starts the day again from the user's plan
  (`startDaysFromUsersPlans`; the sets of the user's own plans are the one thing the reset keeps). Whether a set was done is never copied.
  The pure rules (what counts as a change, how the sets are summed up) are in `domain/plan/PlanMirror.kt` and tested. Both plans are backed
  up, so the trainer sees them, and the dashboard compares an accepted plan with what was sent.
- **Library.** `library/{exerciseId}` in the cloud is pulled into the local `library_exercises` table (pull-only, never
  written by the app). A new install starts from `assets/library.json`, the same file as `admin/seed/library.json`
  (Gradle adds that folder to the app's assets), so the library works offline from the first launch.
- **Coach dashboard.** `admin/` is a Next.js app (React, TypeScript, shadcn/ui) exported as a static site for Firebase
  Hosting. Pages: Overview (who trained this week, who needs a nudge), Clients, Attendance (the last 14 days of every client),
  a client's page (attendance, strength progression per exercise, body weight, daily intake, workouts, plans, activity),
  Activity, Library, Plan library and Team. Its logic is in `admin/src/lib` (`coaching.ts` is the training maths) and tested with Vitest.
  It writes only the two libraries, the team list and the plans it sends. `firestore.rules` is what keeps it that way: an admin is a document in
  `admins/`, reads only the collections listed there (and the all-clients `sessions` and `events` queries), and may not
  write to a client's data.
- **3D body.** `models/anatomy` (built by `tools/anatomy` from BodyParts3D, the Human Reference Atlas and Z-Anatomy data) holds a male and a female
  model with one node per muscle group and side, a skeleton and a smooth see-through skin layer over them (the female skin has a soft bust contour; no breast is drawn), and `muscles.json`, the
  catalogue of 34 muscle groups. The dashboard's Body page (admins can give a muscle a subtitle and a description of their own, kept in `muscles/{muscleId}`, and change which library exercises train it) shows it
  (three.js). The app draws the same files natively (`feature/body`, OpenGL ES 3.0, no web view): `domain/body` holds the pure parts (catalogue, glTF reader,
  camera maths, which muscles a day trains) and `feature/body/gl` the renderer (muscles, see-through skin, pulsing glow, colour-ID picking). The model follows the account's
  gender (`female` shows the female body, anything else the male), and the muscles the chosen day's plan trains glow (main muscles brighter than helpers; exercises are matched to the
  library by name), so the body shows the customer's own training. Tap a muscle to see its description and add exercises for it to the shown day (from the library, into the day's own extra plan, so a trainer's plan is never changed; a day with nothing planned offers a starter day), so the app works with no trainer at all while what a trainer or admin sets in the library and the muscle notes shows up in it. The skeleton can be shown (the unlit muscles step back). The Body screen opens from Profile ("3D body") or from a small arrow tab on the edge of Home that slides out a body button; the admins' muscle notes are pulled into
  `UserPrefs` during sync. In debug builds `BodyPreviewActivity` shows the screen with a made-up week (see its comment). A library exercise names the muscles it trains (`primaryMuscles`, `secondaryMuscles`: catalogue ids,
  in `library/{id}`, set in the dashboard; the starter library in `admin/seed/library.json` has them), which is how a clicked muscle lists its exercises.
- **Emulator tests.** `tools/emulator-tests` runs the rules and the dashboard (in headless Chrome) against the Firebase
  emulators: `cd tools/emulator-tests && npm test`.

## Debug-only tools

Debug builds show demo-data rows in **Profile → Data**: four weeks of workouts, plans, meals and weigh-ins, and two
variants with fast weight loss or gain. Every demo row carries an `isDemo` mark in its own table, so "Remove demo data"
finds exactly those rows, even after a reinstall or on another phone.
