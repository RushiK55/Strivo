'use client';
// Who is signed in. Only accounts listed under admins/ get past the gate; anyone else is signed straight back out.
import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import { onAuthStateChanged, signInWithEmailAndPassword, signOut as firebaseSignOut } from 'firebase/auth';
import { isAdmin } from '@/lib/data';
import { firebaseConfigured, getFirebase } from '@/lib/firebase';

export type AuthState =
  | { status: 'setup' }
  | { status: 'loading' }
  | { status: 'checking' }
  | { status: 'signedOut'; notice: string }
  | { status: 'admin'; uid: string; email: string };

interface AuthApi { state: AuthState; signIn: (email: string, password: string) => Promise<void>; signOut: () => Promise<void> }

const AuthContext = createContext<AuthApi | null>(null);

const NOT_ADMIN = 'This account is not an admin. Ask an existing admin to add you.';

export function AuthProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState<AuthState>(firebaseConfigured ? { status: 'loading' } : { status: 'setup' });
  const notice = useRef('');

  useEffect(() => {
    if (!firebaseConfigured) return undefined;
    const { auth } = getFirebase();
    let active = true;
    const stop = onAuthStateChanged(auth, async (user) => {
      if (!user) {
        const message = notice.current;
        notice.current = '';
        setState({ status: 'signedOut', notice: message });
        return;
      }
      setState({ status: 'checking' });
      let allowed = false;
      try {
        allowed = await isAdmin(user.uid);
      } catch {
        allowed = false;
      }
      if (!active) return;
      if (!allowed) {
        notice.current = NOT_ADMIN;
        await firebaseSignOut(auth);
        return;
      }
      setState({ status: 'admin', uid: user.uid, email: user.email ?? '' });
    });
    return () => { active = false; stop(); };
  }, []);

  const signIn = useCallback(async (email: string, password: string) => {
    await signInWithEmailAndPassword(getFirebase().auth, email, password);
  }, []);
  const signOut = useCallback(() => firebaseSignOut(getFirebase().auth), []);

  const api = useMemo(() => ({ state, signIn, signOut }), [state, signIn, signOut]);
  return <AuthContext.Provider value={api}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthApi {
  const api = useContext(AuthContext);
  if (!api) throw new Error('useAuth must be used inside AuthProvider');
  return api;
}

/** The signed-in admin; only call this inside the gate, where an admin is guaranteed. */
export function useAdmin(): { uid: string; email: string } {
  const { state } = useAuth();
  if (state.status !== 'admin') throw new Error('useAdmin needs a signed-in admin');
  return { uid: state.uid, email: state.email };
}
