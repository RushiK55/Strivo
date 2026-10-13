'use client';
// Short messages in the corner, and yes/no questions: `toast("Saved")`, `await confirm({ ... })`.
import { createContext, useCallback, useContext, useMemo, useRef, useState, type ReactNode } from 'react';
import { Modal } from './Modal';

interface ConfirmOptions { title: string; message: string; confirmText?: string }
interface Toast { id: number; message: string; kind: 'ok' | 'error' }
interface Pending extends ConfirmOptions { resolve: (answer: boolean) => void }

interface FeedbackApi { toast: (message: string, kind?: 'ok' | 'error') => void; confirm: (options: ConfirmOptions) => Promise<boolean> }

const FeedbackContext = createContext<FeedbackApi | null>(null);

export function FeedbackProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<Toast[]>([]);
  const [pending, setPending] = useState<Pending | null>(null);
  const nextId = useRef(1);

  const toast = useCallback((message: string, kind: 'ok' | 'error' = 'ok') => {
    const id = nextId.current++;
    setToasts((current) => [...current, { id, message, kind }]);
    setTimeout(() => setToasts((current) => current.filter((item) => item.id !== id)), 3500);
  }, []);

  const confirm = useCallback((options: ConfirmOptions) => new Promise<boolean>((resolve) => setPending({ ...options, resolve })), []);

  const answer = (value: boolean) => {
    pending?.resolve(value);
    setPending(null);
  };

  const api = useMemo(() => ({ toast, confirm }), [toast, confirm]);
  return (
    <FeedbackContext.Provider value={api}>
      {children}
      {pending ? (
        <Modal title={pending.title} onClose={() => answer(false)}>
          <p>{pending.message}</p>
          <div className="actions">
            <button className="btn" type="button" onClick={() => answer(false)}>Cancel</button>
            <button className="btn btn-danger" type="button" onClick={() => answer(true)}>{pending.confirmText ?? 'Confirm'}</button>
          </div>
        </Modal>
      ) : null}
      <div id="toasts" aria-live="polite">
        {toasts.map((item) => <div key={item.id} className={`toast toast-${item.kind}`}>{item.message}</div>)}
      </div>
    </FeedbackContext.Provider>
  );
}

export function useFeedback(): FeedbackApi {
  const api = useContext(FeedbackContext);
  if (!api) throw new Error('useFeedback must be used inside FeedbackProvider');
  return api;
}
