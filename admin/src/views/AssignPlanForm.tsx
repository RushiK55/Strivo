'use client';
// The form a trainer fills in to send a client a plan. It checks the input with the same rules firestore.rules and the
// app apply (lib/assign.ts), and can start from a library exercise or from one of the client's own plans.
import { Plus, Trash2 } from 'lucide-react';
import { useState, type FormEvent } from 'react';
import { FormField } from '@/components/ui';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { NativeSelect, NativeSelectOptGroup, NativeSelectOption } from '@/components/ui/native-select';
import { Textarea } from '@/components/ui/textarea';
import {
  ASSIGN_DAYS, MAX_ASSIGNED_EXERCISES, blankExercise, exerciseFromLibrary, exerciseFromPlan, validateAssignment,
  type AssignmentDraft, type AssignmentErrors, type DraftExercise,
} from '@/lib/assign';
import { MUSCLE_GROUPS } from '@/lib/logic';
import type { AssignmentFields, ExerciseRow, LibraryExercise, PlanRow } from '@/lib/types';

/** One of the client's plans with its exercises in order, to start a new plan from. */
export interface ClientPlan { plan: PlanRow; exercises: ExerciseRow[] }

export interface AssignPlanFormProps {
  library: LibraryExercise[];
  clientPlans: ClientPlan[];
  /** The trainer's name as the client will see it. */
  initialName: string;
  /** Resolves to an error message, or nothing when the plan was sent. */
  onSend: (value: AssignmentFields) => Promise<string | undefined>;
  onCancel: () => void;
}

const DAYS_FIRST = ['Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday', 'Sunday', 'Prebuilt'];

export function AssignPlanForm({ library, clientPlans, initialName, onSend, onCancel }: AssignPlanFormProps) {
  const [draft, setDraft] = useState<AssignmentDraft>({ planName: '', day: 'Monday', note: '', sentByName: initialName, exercises: [blankExercise()] });
  // After the first try to send, the form is checked as it changes, so a fixed field stops being marked straight away.
  const [tried, setTried] = useState(false);
  const [failure, setFailure] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const check = tried ? validateAssignment(draft) : null;
  const errors: AssignmentErrors = check && !check.ok ? check.errors : {};
  const set = <K extends keyof AssignmentDraft>(key: K, value: AssignmentDraft[K]) => setDraft((current) => ({ ...current, [key]: value }));
  const setRow = (key: number, field: keyof Omit<DraftExercise, 'key'>, value: string) =>
    setDraft((current) => ({ ...current, exercises: current.exercises.map((row) => (row.key === key ? { ...row, [field]: value } : row)) }));
  // A new row replaces the blank one the form starts with, rather than sitting under it.
  const append = (row: DraftExercise) => setDraft((current) => ({
    ...current,
    exercises: [...current.exercises.filter((r) => r.name.trim() || r.notes.trim()), row].slice(0, MAX_ASSIGNED_EXERCISES),
  }));
  const remove = (key: number) => setDraft((current) => ({ ...current, exercises: current.exercises.filter((row) => row.key !== key) }));

  function startFrom(id: string) {
    const source = clientPlans.find((entry) => entry.plan.id === id);
    if (!source) return;
    setDraft((current) => ({
      ...current,
      planName: current.planName || source.plan.planName || '',
      day: DAYS_FIRST.includes(source.plan.planDay ?? '') ? source.plan.planDay! : current.day,
      exercises: source.exercises.length ? source.exercises.map(exerciseFromPlan).slice(0, MAX_ASSIGNED_EXERCISES) : current.exercises,
    }));
  }

  async function submit(event: FormEvent) {
    event.preventDefault();
    setTried(true);
    setFailure(null);
    const result = validateAssignment(draft);
    if (!result.ok) return;
    setBusy(true);
    setFailure((await onSend(result.value)) ?? null);
    setBusy(false);
  }

  const byGroup = new Map<string, LibraryExercise[]>();
  for (const exercise of library.filter((e) => e.active).sort((a, b) => a.name.localeCompare(b.name))) {
    byGroup.set(exercise.muscleGroup, [...(byGroup.get(exercise.muscleGroup) ?? []), exercise]);
  }
  const groups = [...byGroup.keys()].sort((a, b) => (MUSCLE_GROUPS.indexOf(a) + 1 || 99) - (MUSCLE_GROUPS.indexOf(b) + 1 || 99));

  return (
    <form className="grid gap-4" noValidate onSubmit={(event) => void submit(event)} aria-label="Send a plan">
      {clientPlans.length ? (
        <FormField label="Start from one of their plans" hint="Optional. Copies its exercises, which you can then change.">
          {(id) => (
            <NativeSelect id={id} className="w-full" value="" onChange={(event) => startFrom(event.target.value)}>
              <NativeSelectOption value="">Choose a plan…</NativeSelectOption>
              {clientPlans.map(({ plan, exercises }) => (
                <NativeSelectOption key={plan.id} value={plan.id}>{`${plan.planName} (${plan.planDay === 'Prebuilt' ? 'library' : plan.planDay}, ${exercises.length})`}</NativeSelectOption>
              ))}
            </NativeSelect>
          )}
        </FormField>
      ) : null}

      <div className="grid gap-4 sm:grid-cols-2">
        <FormField label="Plan name" error={errors.planName}>
          {(id) => <Input id={id} name="planName" maxLength={60} placeholder="Push Day" aria-invalid={Boolean(errors.planName)} value={draft.planName} onChange={(event) => set('planName', event.target.value)} />}
        </FormField>
        <FormField label="Day" error={errors.day}>
          {(id) => (
            <NativeSelect id={id} className="w-full" name="day" value={draft.day} onChange={(event) => set('day', event.target.value)}>
              {ASSIGN_DAYS.map((day) => <NativeSelectOption key={day.value} value={day.value}>{day.label}</NativeSelectOption>)}
            </NativeSelect>
          )}
        </FormField>
      </div>

      <fieldset className="grid gap-3">
        <legend className="mb-2 text-sm font-medium">Exercises</legend>
        {draft.exercises.map((row, index) => {
          const rowError = errors.rows?.[row.key];
          return (
            <div key={row.key} className="grid gap-2 rounded-lg border p-3 data-[invalid=true]:border-destructive" data-invalid={rowError ? true : undefined}>
              <div className="flex items-center gap-2">
                <Input aria-label={`Exercise ${index + 1} name`} name="exerciseName" maxLength={80} placeholder="Exercise name" value={row.name} onChange={(event) => setRow(row.key, 'name', event.target.value)} />
                <Button type="button" variant="ghost" size="icon" aria-label={`Remove exercise ${index + 1}`} onClick={() => remove(row.key)}><Trash2 /></Button>
              </div>
              <div className="grid grid-cols-4 gap-2">
                {([['sets', 'Sets', 1], ['reps', 'Reps', 1], ['weightKg', 'Kg', 0.5], ['dropSets', 'Drops', 1]] as const).map(([field, label, step]) => (
                  <label key={field} className="grid gap-1 text-xs text-muted-foreground">
                    {label}
                    <Input type="number" inputMode="decimal" min={0} step={step} name={field} aria-label={`Exercise ${index + 1} ${label.toLowerCase()}`} value={row[field]} onChange={(event) => setRow(row.key, field, event.target.value)} />
                  </label>
                ))}
              </div>
              <Input aria-label={`Exercise ${index + 1} notes`} maxLength={500} placeholder="Notes (optional)" value={row.notes} onChange={(event) => setRow(row.key, 'notes', event.target.value)} />
              {rowError ? <p className="text-sm text-destructive" role="alert">{rowError}</p> : null}
            </div>
          );
        })}
        {errors.exercises ? <p className="text-sm text-destructive" role="alert">{errors.exercises}</p> : null}
        <div className="grid gap-2 sm:grid-cols-[auto_1fr]">
          <Button type="button" variant="outline" disabled={draft.exercises.length >= MAX_ASSIGNED_EXERCISES} onClick={() => setDraft((current) => ({ ...current, exercises: [...current.exercises, blankExercise()] }))}>
            <Plus /> Add exercise
          </Button>
          {library.length ? (
            <NativeSelect className="w-full" aria-label="Add from the library" value="" disabled={draft.exercises.length >= MAX_ASSIGNED_EXERCISES}
              onChange={(event) => { const pick = library.find((e) => e.id === event.target.value); if (pick) append(exerciseFromLibrary(pick)); }}>
              <NativeSelectOption value="">Add from the library…</NativeSelectOption>
              {groups.map((group) => (
                <NativeSelectOptGroup key={group} label={group}>
                  {byGroup.get(group)!.map((exercise) => <NativeSelectOption key={exercise.id} value={exercise.id}>{exercise.name}</NativeSelectOption>)}
                </NativeSelectOptGroup>
              ))}
            </NativeSelect>
          ) : null}
        </div>
      </fieldset>

      <FormField label="Note for the client" error={errors.note} hint="Optional. Shown with the plan in the app.">
        {(id) => <Textarea id={id} name="note" rows={2} maxLength={500} value={draft.note} onChange={(event) => set('note', event.target.value)} />}
      </FormField>
      <FormField label="Your name" error={errors.sentByName} hint="How the app says who sent it.">
        {(id) => <Input id={id} name="sentByName" maxLength={100} value={draft.sentByName} onChange={(event) => set('sentByName', event.target.value)} />}
      </FormField>

      {check && !check.ok ? <p className="text-sm text-destructive" role="alert">Check the fields marked above.</p> : null}
      {failure ? <p className="text-sm text-destructive" role="alert">{failure}</p> : null}
      <div className="flex justify-end gap-2">
        <Button variant="outline" type="button" onClick={onCancel}>Cancel</Button>
        <Button type="submit" disabled={busy}>{busy ? 'Sending…' : 'Send plan'}</Button>
      </div>
    </form>
  );
}
