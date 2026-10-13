// Small pieces shared by the pages. Text that came from users (exercise names, notes, activity lines) is untrusted:
// it only ever reaches the page as React text, never as HTML.
import type { ReactNode } from 'react';
import type { Tone } from '@/lib/types';

export function Badge({ tone = 'neutral', children }: { tone?: Tone; children: ReactNode }) {
  return <span className={`badge badge-${tone}`}>{children}</span>;
}

export const Empty = ({ children }: { children: ReactNode }) => <p className="empty">{children}</p>;

export const Loading = ({ text = 'Loading…' }: { text?: string }) => <p className="loading" role="status">{text}</p>;

export function ErrorBox({ error }: { error: unknown }) {
  const code = typeof error === 'object' && error !== null && 'code' in error ? String((error as { code: unknown }).code) : '';
  const message = code === 'permission-denied'
    ? 'Firestore refused the request. Check that firestore.rules is published and that this account is listed under admins.'
    : error instanceof Error ? error.message : String(error);
  return <div className="error" role="alert">{message}</div>;
}

/** A labelled form row: label, the input, and the message for that field when it has one. */
export function Field({ label, error, hint, children }: { label: string; error?: string; hint?: string; children: ReactNode }) {
  return (
    <label className={`field${error ? ' has-error' : ''}`}>
      <span className="field-label">{label}</span>
      {children}
      {hint ? <span className="hint">{hint}</span> : null}
      {error ? <span className="field-error">{error}</span> : null}
    </label>
  );
}

/** A tiny line chart as inline SVG (no library): values in order, drawn left to right. */
export function Sparkline({ values, width = 360, height = 90 }: { values: number[]; width?: number; height?: number }) {
  if (values.length < 2) return <svg className="sparkline" viewBox={`0 0 ${width} ${height}`} role="img" aria-label="Not enough data" />;
  const min = Math.min(...values);
  const span = Math.max(...values) - min || 1;
  const points = values.map((v, i) => `${((i / (values.length - 1)) * (width - 8) + 4).toFixed(1)},${(height - 8 - ((v - min) / span) * (height - 16)).toFixed(1)}`);
  return (
    <svg className="sparkline" viewBox={`0 0 ${width} ${height}`} role="img" aria-label="Chart">
      <polyline points={points.join(' ')} fill="none" stroke="currentColor" strokeWidth="2" />
    </svg>
  );
}

/** The "Load more" button, or the error it ran into. */
export function MoreButton({ hasMore, busy, error, onClick, children }: { hasMore: boolean; busy: boolean; error: unknown; onClick: () => void; children: ReactNode }) {
  return (
    <div className="more">
      {error ? <ErrorBox error={error} /> : null}
      {busy ? <Loading /> : hasMore ? <button className="btn" type="button" onClick={onClick}>{children}</button> : null}
    </div>
  );
}
