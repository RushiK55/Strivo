// Dates and numbers the way the dashboard shows them. Always UTC, so two admins see the same thing.
const valid = (ms: number | undefined): ms is number => typeof ms === 'number' && Number.isFinite(ms) && ms > 0;

export const fmtDate = (ms: number | undefined): string => (valid(ms) ? new Date(ms).toISOString().slice(0, 10) : '–');
export const fmtTime = (ms: number): string => new Date(ms).toISOString().slice(11, 16);
export const fmtNumber = (n: number | undefined): string => (typeof n === 'number' && Number.isFinite(n) ? n.toLocaleString('en-US') : '–');

/** A link target that is only ever https, so stored text can never become a javascript: link. */
export const safeHref = (url: unknown): string | null => (typeof url === 'string' && /^https:\/\/\S+$/i.test(url) ? url : null);

/** "2026-10-13" as "Oct 13", for chart labels. */
export const fmtShortDay = (date: string): string => new Date(`${date}T00:00:00Z`).toLocaleDateString('en-US', { month: 'short', day: 'numeric', timeZone: 'UTC' });
