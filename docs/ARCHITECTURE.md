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

## Debug-only tools

Debug builds show demo-data rows in **Profile → Data**: four weeks of workouts, plans, meals and weigh-ins, and two
variants with fast weight loss or gain. Every demo row carries an `isDemo` mark in its own table, so "Remove demo data"
finds exactly those rows, even after a reinstall or on another phone.
