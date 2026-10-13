# Strivo

Gym workout planner and tracker — native Android, built with **Kotlin** and **Jetpack Compose**.

## Features

- Weekly plans with exercises, sets, reps, weight and drop sets; extra exercises for a single day
- A workout screen with a timer, rest timers, set types (warm-up, drop, failure) and a full-screen focus mode
- Analytics: volume, consistency, per-exercise progress and records, body weight trend, nutrition and energy expenditure
- A coach that suggests the next weight (and a lighter week, protein, weigh-ins) from your own logs
- Food and calorie log, body profile, cloud backup and restore
- An exercise library (74 starter exercises, managed by an admin) that users browse and add to their plans
- An admin web dashboard to follow each user's activity and manage the library (see [admin/README.md](admin/README.md))

## Stack

- Kotlin, Jetpack Compose (Material 3), Navigation Compose
- ViewModel + StateFlow, Kotlin coroutines
- SQLite (`SQLiteOpenHelper`) for plans, exercises, workouts, food and weigh-ins; SharedPreferences for the session and profile
- Firebase Authentication (email + password) and Cloud Firestore (backup and restore)

## Project layout

```
app/src/main/java/com/example/strivo/
  navigation/   routes, nav graph, bottom bar
  feature/      one package per screen area (home, workout, plans, analytics, food, history, profile, auth, coach)
  core/         shared theme, UI components and formatting
  domain/       models and the app's rules (analytics, coach, plan status), plain Kotlin
  data/         SQLite, preferences, Firebase, sync, repository, demo data

admin/          the admin web dashboard (Next.js, exported as a static site for Firebase Hosting)
tools/          tests that run on the Firebase emulators (security rules, the dashboard in a browser)
```

Dependencies only point downwards, and a unit test enforces it. See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for the
layers, the rules, how a screen is built, and how to add a feature.

## Build and test

Open the folder in Android Studio, or from a terminal:

```
./gradlew :app:assembleDebug          # debug APK
./gradlew :app:testDebugUnitTest      # unit tests, including the architecture rules
./gradlew :app:assembleRelease        # minified release build (signed with the debug key for now)
```

Requires JDK 17+ and the Android SDK (compileSdk 37).

## Firebase setup

1. In the [Firebase console](https://console.firebase.google.com) create a project and add an **Android app** with package name `strivo.app`.
2. **Authentication → Sign-in method →** enable **Email/Password**.
3. Download `google-services.json` and put it in `app/` (next to `app/build.gradle.kts`).
4. Rebuild. The Realtime Database URL, if you ever need it, is the `firebase_url` field inside that file — Authentication itself doesn't use it.

## Cloud sync (Firestore)

Each account's data is backed up to **Cloud Firestore** under `users/{uid}`, and the phone keeps working offline.

- The app reads and writes the local SQLite database. SQLite triggers record every change in a `sync_log` table.
- Whenever the phone is online, the log is uploaded to Firestore, then the cloud is read back. A new phone or a
  reinstall gets its data (and profile) restored on sign-in. Changes not yet uploaded are never overwritten.
- Firestore's own offline cache is disabled on purpose; the local database is the offline store.

One-time setup in the Firebase console for this project:

1. **Build → Firestore Database → Create database** (production mode).
2. **Rules**: paste the contents of `firestore.rules` and publish.

Until that is done the app still works fully offline, and Profile → "Cloud sync" shows what is wrong.

## Admin dashboard and exercise library

The admin dashboard is a separate web app in `admin/`. Setting it up means publishing `firestore.rules`, adding a Web app
config, creating the first admin and deploying the site; the steps are in [admin/README.md](admin/README.md).

The phone app records what a user does (a plain-language activity log) and a short summary of who they are, both backed
up under `users/{uid}`. The library lives in `library/{exerciseId}`; every phone keeps a copy and starts with the 74
exercises from `admin/seed/library.json`, which are bundled into the app.

## Firebase config (not in git)

`app/google-services.json` holds this project's Firebase settings and API key, so it is git-ignored.
Download your own copy from the Firebase console (Project settings → Your apps → Android) and put it in `app/`.
`app/google-services.json.example` shows the expected shape.
