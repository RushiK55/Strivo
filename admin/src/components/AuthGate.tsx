'use client';
// Shows the sign-in form until an admin is signed in, and the page after that.
import { useState, type FormEvent, type ReactNode } from 'react';
import { useAuth } from './AuthProvider';
import { Field, Loading } from './ui';

const WRONG = ['auth/invalid-credential', 'auth/wrong-password', 'auth/user-not-found', 'auth/invalid-email'];

export function AuthGate({ children }: { children: ReactNode }) {
  const { state } = useAuth();
  switch (state.status) {
    case 'admin':
      return children;
    case 'setup':
      return <Setup />;
    case 'signedOut':
      return <SignIn notice={state.notice} />;
    case 'checking':
      return <Centered><Loading text="Checking your access…" /></Centered>;
    default:
      return <Centered><Loading /></Centered>;
  }
}

const Centered = ({ children }: { children: ReactNode }) => <main className="center">{children}</main>;

function Setup() {
  return (
    <Centered>
      <div className="card narrow">
        <h1>Almost there</h1>
        <p>This dashboard needs your Firebase project settings. Copy <code>.env.example</code> to <code>.env.local</code>, fill it in, and build again.</p>
        <p className="muted">Firebase console → Project settings → Your apps → Web app.</p>
      </div>
    </Centered>
  );
}

function SignIn({ notice }: { notice: string }) {
  const { signIn } = useAuth();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  async function submit(event: FormEvent) {
    event.preventDefault();
    setBusy(true);
    setError('');
    try {
      await signIn(email.trim(), password);
    } catch (failure) {
      const code = typeof failure === 'object' && failure !== null && 'code' in failure ? String((failure as { code: unknown }).code) : '';
      setError(WRONG.includes(code) ? 'Wrong email or password.' : failure instanceof Error ? failure.message : String(failure));
      setBusy(false);
    }
  }

  const message = error || notice;
  return (
    <Centered>
      <div className="card narrow">
        <h1>Strivo Admin</h1>
        <p className="muted">Sign in with an admin account.</p>
        <form className="form" onSubmit={submit}>
          <Field label="Email">
            <input type="email" name="email" autoComplete="username" required autoFocus value={email} onChange={(event) => setEmail(event.target.value)} />
          </Field>
          <Field label="Password">
            <input type="password" name="password" autoComplete="current-password" required value={password} onChange={(event) => setPassword(event.target.value)} />
          </Field>
          <div role="alert">{message ? <div className="error">{message}</div> : null}</div>
          <button className="btn btn-primary" type="submit" disabled={busy}>Sign in</button>
        </form>
      </div>
    </Centered>
  );
}
