# Strivo

Gym workout planner and tracker — native Android, built with **Kotlin** and **Jetpack Compose**.

## Stack

- Kotlin, Jetpack Compose (Material 3), Navigation Compose
- ViewModel + StateFlow, Kotlin coroutines
- SQLite (`SQLiteOpenHelper`) for plans, exercises and workout history — same schema/file as the old Flutter build, so existing data carries over
- SharedPreferences for the login session and body profile
- Firebase Authentication (email + password) for sign-in / sign-up

## Project layout

```
app/src/main/java/com/example/strivo/
  data/        models, SQLite database, repository, preferences, Firebase auth service
  viewmodel/   Auth, Plan, Exercise, Profile and Workout view models
  ui/          theme, shared components (wheel picker, headers…), screens, navigation
```

## Build

Open the folder in Android Studio, or from a terminal:

```
./gradlew :app:assembleDebug
```

Requires JDK 17+ and the Android SDK (compileSdk 37).

## Firebase setup

1. In the [Firebase console](https://console.firebase.google.com) create a project and add an **Android app** with package name `com.example.strivo`.
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

## Firebase config (not in git)

`app/google-services.json` holds this project's Firebase settings and API key, so it is git-ignored.
Download your own copy from the Firebase console (Project settings → Your apps → Android) and put it in `app/`.
`app/google-services.json.example` shows the expected shape.
