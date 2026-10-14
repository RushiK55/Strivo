// Small pieces shared by the pages, built from the shadcn components in ./ui. Text that came from users (exercise names,
// notes, activity lines) is untrusted: it only ever reaches the page as React text, never as HTML.
import { Search, type LucideIcon } from 'lucide-react';
import { useId, type ReactNode } from 'react';
import { Alert, AlertDescription } from '@/components/ui/alert';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card, CardContent } from '@/components/ui/card';
import { Empty as EmptyBox, EmptyDescription, EmptyHeader } from '@/components/ui/empty';
import { Field, FieldDescription, FieldError, FieldLabel } from '@/components/ui/field';
import { InputGroup, InputGroupAddon, InputGroupInput } from '@/components/ui/input-group';
import { Skeleton } from '@/components/ui/skeleton';
import { ToggleGroup, ToggleGroupItem } from '@/components/ui/toggle-group';
import type { Tone } from '@/lib/types';
import { cn } from '@/lib/utils';

const TONE_VARIANT = { neutral: 'secondary', good: 'success', info: 'info', warn: 'warning', bad: 'destructive' } as const;

/** A small pill whose colour says what kind of thing it is. */
export function ToneBadge({ tone = 'neutral', children }: { tone?: Tone; children: ReactNode }) {
  return <Badge variant={TONE_VARIANT[tone]}>{children}</Badge>;
}

/** A page: its title, a line under it, buttons on the right, and the content. */
export function Page({ title, description, actions, children }: { title: string; description?: string; actions?: ReactNode; children: ReactNode }) {
  return (
    <div className="mx-auto w-full max-w-7xl space-y-6 p-4 md:p-8">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h1>{title}</h1>
          {description ? <p className="mt-1 text-muted-foreground">{description}</p> : null}
        </div>
        {actions ? <div className="flex flex-wrap items-center gap-2">{actions}</div> : null}
      </div>
      {children}
    </div>
  );
}

export function Empty({ children }: { children: ReactNode }) {
  return (
    <EmptyBox className="border border-dashed p-6">
      <EmptyHeader>
        <EmptyDescription>{children}</EmptyDescription>
      </EmptyHeader>
    </EmptyBox>
  );
}

export function Loading({ text = 'Loading…' }: { text?: string }) {
  return (
    <div role="status" className="space-y-3 py-2">
      <span className="sr-only">{text}</span>
      <Skeleton className="h-6 w-1/3" />
      <Skeleton className="h-28 w-full" />
    </div>
  );
}

/** What a page shows while it loads: a row of figures and a table. */
export function PageSkeleton({ stats = 0 }: { stats?: number }) {
  return (
    <div role="status" className="space-y-4">
      <span className="sr-only">Loading…</span>
      {stats ? (
        <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
          {Array.from({ length: stats }, (_, i) => <Skeleton key={i} className="h-24 rounded-xl" />)}
        </div>
      ) : null}
      <Skeleton className="h-72 rounded-xl" />
    </div>
  );
}

export function ErrorBox({ error }: { error: unknown }) {
  const code = typeof error === 'object' && error !== null && 'code' in error ? String((error as { code: unknown }).code) : '';
  const message = code === 'permission-denied'
    ? 'Firestore refused the request. Check that firestore.rules is published and that this account is listed under admins.'
    : error instanceof Error ? error.message : String(error);
  return (
    <Alert variant="destructive">
      <AlertDescription>{message}</AlertDescription>
    </Alert>
  );
}

/** A figure with a label, an icon and an optional note, for the cards at the top of a page. */
export function Stat({ label, value, icon: Icon, note }: { label: string; value: string | number; icon?: LucideIcon; note?: string }) {
  return (
    <Card size="sm">
      <CardContent className="flex items-start justify-between gap-3">
        <div className="flex min-w-0 flex-col gap-0.5">
          <span className="text-sm text-muted-foreground">{label}</span>
          <span className="truncate text-3xl font-bold tracking-tight tabular-nums">{value}</span>
          {note ? <span className="text-xs text-muted-foreground">{note}</span> : null}
        </div>
        {Icon ? (
          <span className="grid size-9 flex-none place-items-center rounded-lg bg-primary/15 text-primary"><Icon className="size-5" aria-hidden="true" /></span>
        ) : null}
      </CardContent>
    </Card>
  );
}

/** A labelled form row. `children` receives the id to put on the input, so the label points at it. */
export function FormField({ label, error, hint, children }: { label: string; error?: string; hint?: string; children: (id: string) => ReactNode }) {
  const id = useId();
  return (
    <Field data-invalid={error ? true : undefined}>
      <FieldLabel htmlFor={id}>{label}</FieldLabel>
      {children(id)}
      {hint ? <FieldDescription>{hint}</FieldDescription> : null}
      {error ? <FieldError>{error}</FieldError> : null}
    </Field>
  );
}

/** A search box with a magnifying glass. */
export function SearchBox({ value, onChange, placeholder, label, className }: { value: string; onChange: (value: string) => void; placeholder: string; label: string; className?: string }) {
  return (
    <InputGroup className={cn('w-full sm:w-72', className)}>
      <InputGroupAddon><Search aria-hidden="true" /></InputGroupAddon>
      <InputGroupInput type="search" placeholder={placeholder} aria-label={label} value={value} onChange={(event) => onChange(event.target.value)} />
    </InputGroup>
  );
}

export interface FilterOption<T extends string> { value: T; label: string; count?: number }

/** A row of buttons of which exactly one is chosen, with an optional count on each. */
export function FilterToggle<T extends string>({ value, onChange, options, label }: { value: T; onChange: (value: T) => void; options: FilterOption<T>[]; label: string }) {
  return (
    <ToggleGroup
      aria-label={label} value={[value]} variant="outline" size="sm" spacing={1} className="flex-wrap"
      onValueChange={(next) => { const chosen = next[0]; if (chosen !== undefined) onChange(chosen as T); }}
    >
      {options.map((option) => (
        <ToggleGroupItem
          key={option.value} value={option.value}
          className="aria-pressed:border-primary/40 aria-pressed:bg-primary/15 aria-pressed:text-primary"
        >
          {option.label}
          {option.count !== undefined ? <span className="ml-1 text-xs text-muted-foreground tabular-nums">{option.count}</span> : null}
        </ToggleGroupItem>
      ))}
    </ToggleGroup>
  );
}

/** A tiny line chart as inline SVG (no library): values in order, drawn left to right. */
export function Sparkline({ values, width = 360, height = 90 }: { values: number[]; width?: number; height?: number }) {
  const className = 'sparkline my-2 block h-[90px] w-full max-w-lg text-primary';
  if (values.length < 2) return <svg className={className} viewBox={`0 0 ${width} ${height}`} role="img" aria-label="Not enough data" />;
  const min = Math.min(...values);
  const span = Math.max(...values) - min || 1;
  const points = values.map((v, i) => `${((i / (values.length - 1)) * (width - 8) + 4).toFixed(1)},${(height - 8 - ((v - min) / span) * (height - 16)).toFixed(1)}`);
  return (
    <svg className={className} viewBox={`0 0 ${width} ${height}`} role="img" aria-label="Chart">
      <polyline points={points.join(' ')} fill="none" stroke="currentColor" strokeWidth="2" />
    </svg>
  );
}

/** The "Load more" button, or the error it ran into. */
export function MoreButton({ hasMore, busy, error, onClick, children }: { hasMore: boolean; busy: boolean; error: unknown; onClick: () => void; children: ReactNode }) {
  if (!hasMore && !busy && !error) return null;
  return (
    <div className="space-y-2">
      {error ? <ErrorBox error={error} /> : null}
      {busy ? <Loading /> : hasMore ? <Button variant="outline" type="button" onClick={onClick}>{children}</Button> : null}
    </div>
  );
}
