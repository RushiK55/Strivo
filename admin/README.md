# Strivo Admin

A web dashboard for the person who runs Strivo. It shows every user and what they have done, and it manages the
exercise library that all users can browse in the app. It is a Next.js app (React, TypeScript) exported as a static
site and hosted on Firebase Hosting; it talks straight to the same Firebase project as the app, so there is no server
to run.

| Page | What it is for |
|---|---|
| **Overview** | Users, who was active in the last 7 days, actions in the last 24 hours, recent activity |
| **Users** | Everyone, most recently active first; search by name, email or last action |
| **A user** | Their activity timeline, finished workouts (with sets), plans, weight over time, food log |
| **Activity** | What all users added or changed, newest first, filterable by kind |
| **Library** | Add, edit, hide and delete library exercises; import the starter library |
| **Admins** | Who can use the dashboard |

An admin **reads** user data but cannot change it (the rules enforce this, not just the page). The only things an admin
writes are library exercises and the admin list.

## One-time setup

Needs Node 20.9 or newer (22 to run the tests) and the Firebase CLI (`npm install -g firebase-tools`).

1. **Publish the rules and indexes** (they hold the admin rules and the library):

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
src/views/       what each page shows: Overview, Users, UserDetail (+ tabs), Activity, Library (+ form), Admins
src/components/  sign-in gate, sidebar frame, dialogs and messages, activity list, small UI pieces
src/lib/         logic.ts (pure, tested), data.ts (every Firestore read and write), firebase.ts, hooks, types
seed/library.json  the 74 starter exercises (the Android app bundles the same file)
```

A user's page is `/users/view/?uid=...` rather than `/users/<uid>`, because a static site cannot have a page for every
account id in advance.

## What it relies on

- **Activity**: the app writes one line per thing a user does to `users/{uid}/events` (see `ActivityLog.kt`). Users who
  have not opened the new version of the app have no summary yet and do not appear in the Users list.
- **User summary**: `users/{uid}` (name, email, last active, app version, last action), written by the app at most every
  five minutes while it syncs.
- **Library**: `library/{exerciseId}`. The app copies it to the phone and shows only exercises that are *shown*.
- **Security**: `firestore.rules` at the project root. Text from users is only ever displayed as text (nothing in the
  dashboard uses `dangerouslySetInnerHTML`), and the hosting headers (`firebase.json`) set a content security policy
  that only allows scripts from this site. Next's static export starts the page with small inline scripts, so the policy
  has to allow `'unsafe-inline'` for scripts; that is why the "text only" rule above matters.

## Privacy

An admin can see what users add and do. Tell users: the app shows a note in Profile, and your privacy policy should say
that account data (plans, workouts, food, weight and activity) is stored in your Firebase project and visible to
administrators for support and to improve the app. Keep the admin list short, and remove people who no longer need access.

## Tests

```
cd admin && npm test                 # the dashboard's logic (no Firebase needed)
cd tools/emulator-tests && npm test  # the security rules, and the dashboard in a real browser, on the emulators
```
