# Architecture

Strivo is a single-module Android app (Kotlin, Jetpack Compose, Material 3). The code is organised by **layer** first
and by **feature** second, and one rule runs through all of it: **dependencies only point downwards**.
`ArchitectureTest` fails the build when a wrong import is written, so this document and the code cannot drift apart.

```
app/src/main/java/com/example/strivo/
├── MainActivity.kt, StrivoApp.kt     entry points; StrivoApp wires the data layer once per signed-in account
├── navigation/                       the nav graph, routes and the bottom bar; the only place that knows every screen
├── feature/                          one package per screen area: screens, view models and their parts
│   ├── home/  workout/  plans/  analytics/  food/  history/  profile/  auth/  coach/
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
`home → plans, profile, auth, coach`, `analytics → coach`, `history → plans`, `profile → auth`.

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
- **Plans from the trainer.** A trainer sends a plan from the dashboard to `users/{uid}/assigned/{id}` (status `sent`).
  `SyncManager.pullAssignedPlans` reads them on each pull, adds each one to the local plans once (`StrivoDatabase.importPlan`,
  with a `plan_assigned` line in the activity log), remembers its id (`UserPrefs`), then marks it `delivered`. The pure part
  (reading and clamping the document, the received list) is `domain/plan/PlanAssignment.kt`. Home shows a "From your
  trainer" card (`feature/home/TrainerPlansCard.kt`) for the plans received in the last two weeks until the user hides it.
  The added plan is an ordinary plan from then on: the user can change or delete it.
- **Library.** `library/{exerciseId}` in the cloud is pulled into the local `library_exercises` table (pull-only, never
  written by the app). A new install starts from `assets/library.json`, the same file as `admin/seed/library.json`
  (Gradle adds that folder to the app's assets), so the library works offline from the first launch.
- **Coach dashboard.** `admin/` is a Next.js app (React, TypeScript, shadcn/ui) exported as a static site for Firebase
  Hosting. Pages: Overview (who trained this week, who needs a nudge), Clients, Attendance (the last 14 days of every client),
  a client's page (attendance, strength progression per exercise, body weight, daily intake, workouts, plans, activity),
  Activity, Library and Team. Its logic is in `admin/src/lib` (`coaching.ts` is the training maths) and tested with Vitest.
  It writes only the library, the team list and the plans it sends. `firestore.rules` is what keeps it that way: an admin is a document in
  `admins/`, reads only the collections listed there (and the all-clients `sessions` and `events` queries), and may not
  write to a client's data.
- **3D body.** `models/anatomy` (built by `tools/anatomy` from BodyParts3D, the Human Reference Atlas and Z-Anatomy data) holds a male and a female
  model with one node per muscle group and side, a skeleton and a smooth see-through skin layer over them (the female skin has a soft bust contour; no breast is drawn), and `muscles.json`, the
  catalogue of 34 muscle groups. The dashboard's Body page shows it
  (three.js); the app will use the same files. A library exercise names the muscles it trains (`primaryMuscles`, `secondaryMuscles`: catalogue ids,
  in `library/{id}`, set in the dashboard; the starter library in `admin/seed/library.json` has them), which is how a clicked muscle lists its exercises.
- **Emulator tests.** `tools/emulator-tests` runs the rules and the dashboard (in headless Chrome) against the Firebase
  emulators: `cd tools/emulator-tests && npm test`.

## Debug-only tools

Debug builds show demo-data rows in **Profile → Data**: four weeks of workouts, plans, meals and weigh-ins, and two
variants with fast weight loss or gain. Every demo row carries an `isDemo` mark in its own table, so "Remove demo data"
finds exactly those rows, even after a reinstall or on another phone.
