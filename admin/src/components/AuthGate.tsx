'use client';
// Shows the sign-in form until an admin is signed in, and the page after that.
import { useState, type FormEvent, type ReactNode } from 'react';
import { Alert, AlertDescription } from '@/components/ui/alert';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { useAuth } from './AuthProvider';
import { FormField, Loading } from './ui';

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

const Centered = ({ children }: { children: ReactNode }) => <main className="grid min-h-screen place-items-center p-6"><div className="w-full max-w-sm">{children}</div></main>;

function Setup() {
  return (
    <Centered>
      <Card>
        <CardHeader>
          <CardTitle className="text-xl">Almost there</CardTitle>
          <CardDescription>
            This dashboard needs your Firebase project settings. Copy <code>.env.example</code> to <code>.env.local</code>, fill it in, and build again.
          </CardDescription>
        </CardHeader>
        <CardContent className="text-muted-foreground">Firebase console → Project settings → Your apps → Web app.</CardContent>
      </Card>
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
      <Card>
        <CardHeader>
          <CardTitle className="text-xl">Strivo <span className="text-primary">Admin</span></CardTitle>
          <CardDescription>Sign in with an admin account.</CardDescription>
        </CardHeader>
        <CardContent>
          <form className="grid gap-4" onSubmit={(event) => void submit(event)}>
            <FormField label="Email">
              {(id) => <Input id={id} type="email" name="email" autoComplete="username" required autoFocus value={email} onChange={(event) => setEmail(event.target.value)} />}
            </FormField>
            <FormField label="Password">
              {(id) => <Input id={id} type="password" name="password" autoComplete="current-password" required value={password} onChange={(event) => setPassword(event.target.value)} />}
            </FormField>
            <div role="alert">{message ? <Alert variant="destructive"><AlertDescription>{message}</AlertDescription></Alert> : null}</div>
            <Button type="submit" disabled={busy}>Sign in</Button>
          </form>
        </CardContent>
      </Card>
    </Centered>
  );
}
