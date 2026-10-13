// The one place that starts the Firebase SDK. The settings come from NEXT_PUBLIC_FIREBASE_* at build time; they are not
// secrets (they ship in every web page), the security rules and the admin list are what protect the data.
import { getApp, getApps, initializeApp, type FirebaseApp } from 'firebase/app';
import { connectAuthEmulator, getAuth, type Auth } from 'firebase/auth';
import { connectFirestoreEmulator, getFirestore, type Firestore } from 'firebase/firestore';

// Each variable is read by its full name so Next can inline it into the page.
const config = {
  apiKey: process.env.NEXT_PUBLIC_FIREBASE_API_KEY,
  authDomain: process.env.NEXT_PUBLIC_FIREBASE_AUTH_DOMAIN,
  projectId: process.env.NEXT_PUBLIC_FIREBASE_PROJECT_ID,
  appId: process.env.NEXT_PUBLIC_FIREBASE_APP_ID,
};

export const firebaseConfigured = Boolean(config.apiKey && config.authDomain && config.projectId && config.appId);

// Emulators are only for trying the dashboard locally (tools/emulator-tests); never set this for the real project.
const useEmulators = process.env.NEXT_PUBLIC_FIREBASE_EMULATORS === '1';

export interface Firebase { app: FirebaseApp; auth: Auth; db: Firestore }

let cached: Firebase | undefined;

export function getFirebase(): Firebase {
  if (cached) return cached;
  if (!firebaseConfigured) throw new Error('Firebase is not configured. Copy .env.example to .env.local and fill it in.');
  const fresh = getApps().length === 0;
  const app = fresh ? initializeApp(config) : getApp();
  const auth = getAuth(app);
  const db = getFirestore(app);
  // Only the first start may connect to the emulators; a second connect (hot reload) throws.
  if (useEmulators && fresh) {
    connectAuthEmulator(auth, 'http://127.0.0.1:9099', { disableWarnings: true });
    connectFirestoreEmulator(db, '127.0.0.1', 8080);
  }
  cached = { app, auth, db };
  return cached;
}
