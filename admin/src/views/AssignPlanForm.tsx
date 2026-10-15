'use client';
// The form a trainer fills in to send a client a plan. It checks the input with the same rules firestore.rules and the
// app apply (lib/assign.ts), and can start from a plan of the plan library, from one of the client's own plans, and add exercises
// from the exercise library.
import { useState, type FormEvent } from 'react';
import { ExerciseRowsEditor } from '@/components/ExerciseRowsEditor';
import { FormField } from '@/components/ui';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { NativeSelect, NativeSelectOption } from '@/components/ui/native-select';
import { Textarea } from '@/components/ui/textarea';
import {
  ASSIGN_DAYS, MAX_ASSIGNED_EXERCISES, blankExercise, exerciseFromPlan, exercisesFromLibraryPlan, validateAssignment,
  type AssignmentDraft, type AssignmentErrors,
} from '@/lib/assign';
import { exerciseCount } from '@/lib/planLibrary';
import type { AssignmentFields, ExerciseRow, LibraryExercise, LibraryPlan, PlanRow } from '@/lib/types';

/** One of the client's plans with its exercises in order, to start a new plan from. */
export interface ClientPlan { plan: PlanRow; exercises: ExerciseRow[] }

export interface AssignPlanFormProps {
  library: LibraryExercise[];
  /** The plan library, to start from. */
  planLibrary: LibraryPlan[];
  clientPlans: ClientPlan[];
  /** The trainer's name as the client will see it. */
  initialName: string;
  /** Resolves to an error message, or nothing when the plan was sent. */
  onSend: (value: AssignmentFields) => Promise<string | undefined>;
  onCancel: () => void;
}

const DAYS_FIRST = ['Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday', 'Sunday', 'Prebuilt'];

export function AssignPlanForm({ library, planLibrary, clientPlans, initialName, onSend, onCancel }: AssignPlanFormProps) {
  const [draft, setDraft] = useState<AssignmentDraft>({ planName: '', day: 'Monday', note: '', sentByName: initialName, exercises: [blankExercise()] });
  // After the first try to send, the form is checked as it changes, so a fixed field stops being marked straight away.
  const [tried, setTried] = useState(false);
  const [failure, setFailure] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const check = tried ? validateAssignment(draft) : null;
  const errors: AssignmentErrors = check && !check.ok ? check.errors : {};
  const set = <K extends keyof AssignmentDraft>(key: K, value: AssignmentDraft[K]) => setDraft((current) => ({ ...current, [key]: value }));

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

  /** Starts from a plan of the plan library: its name (unless one is typed), and its exercises, which can then be changed. */
  function startFromLibrary(id: string) {
    const source = planLibrary.find((entry) => entry.id === id);
    if (!source) return;
    setDraft((current) => ({
      ...current,
      planName: current.planName || source.name,
      exercises: exercisesFromLibraryPlan(source).length ? exercisesFromLibraryPlan(source) : current.exercises,
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

  return (
    <form className="grid gap-4" noValidate onSubmit={(event) => void submit(event)} aria-label="Send a plan">
      {planLibrary.length ? (
        <FormField label="Start from the plan library" hint="Optional. Copies its exercises, which you can then change.">
          {(id) => (
            <NativeSelect id={id} className="w-full" value="" onChange={(event) => startFromLibrary(event.target.value)}>
              <NativeSelectOption value="">Choose a plan…</NativeSelectOption>
              {planLibrary.filter((plan) => plan.active).sort((a, b) => a.name.localeCompare(b.name)).map((plan) => (
                <NativeSelectOption key={plan.id} value={plan.id}>{`${plan.name} (${exerciseCount(plan)}${plan.level ? `, ${plan.level}` : ''})`}</NativeSelectOption>
              ))}
            </NativeSelect>
          )}
        </FormField>
      ) : null}

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

      <ExerciseRowsEditor
        rows={draft.exercises}
        onChange={(exercises) => set('exercises', exercises)}
        library={library}
        listError={errors.exercises}
        rowErrors={errors.rows}
      />

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
