'use client';
// The form for adding or editing one plan of the plan library. Its exercises are picked from the exercise library (or typed), and it
// checks the input with the same rules firestore.rules enforces.
import { useState, type FormEvent } from 'react';
import { ExerciseRowsEditor } from '@/components/ExerciseRowsEditor';
import { FormField } from '@/components/ui';
import { Button } from '@/components/ui/button';
import { Checkbox } from '@/components/ui/checkbox';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { NativeSelect, NativeSelectOption } from '@/components/ui/native-select';
import { Textarea } from '@/components/ui/textarea';
import { LEVELS } from '@/lib/logic';
import { blankPlanDraft, validatePlanLibrary, type PlanLibraryDraft, type PlanLibraryErrors } from '@/lib/planLibrary';
import type { LibraryExercise, LibraryPlanFields } from '@/lib/types';

export interface PlanLibraryFormProps {
  /** The plan being edited, as a draft; null for a new one. */
  initial: PlanLibraryDraft | null;
  /** The exercise library, to build the plan from. */
  library: LibraryExercise[];
  /** Called with the checked values. Resolves to an error for the Name field, or nothing when saved. */
  onSave: (value: LibraryPlanFields) => Promise<string | undefined>;
  onCancel: () => void;
}

export function PlanLibraryForm({ initial, library, onSave, onCancel }: PlanLibraryFormProps) {
  const [values, setValues] = useState<PlanLibraryDraft>(initial ?? blankPlanDraft());
  // After the first try to save, the form is checked as it changes, so a fixed field stops being marked straight away.
  const [tried, setTried] = useState(false);
  const [refused, setRefused] = useState<string | undefined>();
  const [busy, setBusy] = useState(false);
  const check = tried ? validatePlanLibrary(values) : null;
  const errors: PlanLibraryErrors = { ...(check && !check.ok ? check.errors : {}), ...(refused ? { name: refused } : {}) };
  const set = <K extends keyof PlanLibraryDraft>(key: K, value: PlanLibraryDraft[K]) => {
    if (key === 'name') setRefused(undefined);
    setValues((current) => ({ ...current, [key]: value }));
  };

  async function submit(event: FormEvent) {
    event.preventDefault();
    setTried(true);
    const result = validatePlanLibrary(values);
    if (!result.ok) return;
    setBusy(true);
    setRefused(await onSave(result.value));
    setBusy(false);
  }

  const levels = [...new Set([...LEVELS, values.level].filter(Boolean))];
  return (
    <form className="grid gap-4" noValidate onSubmit={(event) => void submit(event)} aria-label="Plan">
      <div className="grid gap-4 sm:grid-cols-[1fr_auto]">
        <FormField label="Name" error={errors.name}>
          {(id) => <Input id={id} name="name" maxLength={60} placeholder="Push Day" aria-invalid={Boolean(errors.name)} value={values.name} onChange={(event) => set('name', event.target.value)} />}
        </FormField>
        <FormField label="Level" error={errors.level}>
          {(id) => (
            <NativeSelect id={id} className="w-full" name="level" value={values.level} onChange={(event) => set('level', event.target.value)}>
              {levels.map((level) => <NativeSelectOption key={level} value={level}>{level}</NativeSelectOption>)}
            </NativeSelect>
          )}
        </FormField>
      </div>
      <FormField label="Description" error={errors.description} hint="Optional. Shown with the plan in the app.">
        {(id) => <Textarea id={id} name="description" rows={2} maxLength={500} value={values.description} onChange={(event) => set('description', event.target.value)} />}
      </FormField>

      <ExerciseRowsEditor
        rows={values.exercises}
        onChange={(exercises) => set('exercises', exercises)}
        library={library}
        listError={errors.exercises}
        rowErrors={errors.rows}
      />

      <div className="flex items-center gap-2">
        <Checkbox id="plan-active" checked={values.active} onCheckedChange={(checked) => set('active', checked === true)} />
        <Label htmlFor="plan-active">Shown to users in the app</Label>
      </div>

      <div className="flex justify-end gap-2">
        <Button variant="outline" type="button" onClick={onCancel}>Cancel</Button>
        <Button type="submit" disabled={busy}>{busy ? 'Saving…' : 'Save plan'}</Button>
      </div>
    </form>
  );
}
