'use client';
import type { ReactNode } from 'react';
import { AuthGate } from './AuthGate';
import { AuthProvider } from './AuthProvider';
import { FeedbackProvider } from './Feedback';
import { Shell } from './Shell';

/** Everything every page needs: the sign-in gate, the frame, and the corner messages. */
export function Providers({ children }: { children: ReactNode }) {
  return (
    <AuthProvider>
      <FeedbackProvider>
        <AuthGate>
          <Shell>{children}</Shell>
        </AuthGate>
      </FeedbackProvider>
    </AuthProvider>
  );
}
