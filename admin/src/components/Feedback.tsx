'use client';
// Short messages in the corner (sonner), and yes/no questions (shadcn AlertDialog):
// `toast("Saved")`, `await confirm({ ... })`.
import { createContext, useCallback, useContext, useMemo, useState, type ReactNode } from 'react';
import { toast as showToast } from 'sonner';
import {
  AlertDialog, AlertDialogAction, AlertDialogCancel, AlertDialogContent, AlertDialogDescription, AlertDialogFooter, AlertDialogHeader, AlertDialogTitle,
} from '@/components/ui/alert-dialog';
import { Toaster } from '@/components/ui/sonner';

interface ConfirmOptions { title: string; message: string; confirmText?: string }
interface Pending extends ConfirmOptions { resolve: (answer: boolean) => void }

interface FeedbackApi { toast: (message: string, kind?: 'ok' | 'error') => void; confirm: (options: ConfirmOptions) => Promise<boolean> }

const FeedbackContext = createContext<FeedbackApi | null>(null);

export function FeedbackProvider({ children }: { children: ReactNode }) {
  const [pending, setPending] = useState<Pending | null>(null);

  const toast = useCallback((message: string, kind: 'ok' | 'error' = 'ok') => {
    if (kind === 'error') showToast.error(message);
    else showToast.success(message);
  }, []);

  const confirm = useCallback((options: ConfirmOptions) => new Promise<boolean>((resolve) => setPending({ ...options, resolve })), []);

  // Answering twice is harmless: a promise only takes the first answer (the action resolves true, then the dialog closes).
  const answer = (value: boolean) => {
    pending?.resolve(value);
    setPending(null);
  };

  const api = useMemo(() => ({ toast, confirm }), [toast, confirm]);
  return (
    <FeedbackContext.Provider value={api}>
      {children}
      <AlertDialog open={pending !== null} onOpenChange={(open) => { if (!open) answer(false); }}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>{pending?.title}</AlertDialogTitle>
            <AlertDialogDescription>{pending?.message}</AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>Cancel</AlertDialogCancel>
            <AlertDialogAction variant="destructive" onClick={() => answer(true)}>{pending?.confirmText ?? 'Confirm'}</AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
      <Toaster position="bottom-right" />
    </FeedbackContext.Provider>
  );
}

export function useFeedback(): FeedbackApi {
  const api = useContext(FeedbackContext);
  if (!api) throw new Error('useFeedback must be used inside FeedbackProvider');
  return api;
}
