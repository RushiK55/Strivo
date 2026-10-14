'use client';
// The form for adding or editing one library exercise. It checks the input with the same rules firestore.rules enforces.
import { useState, type FormEvent } from 'react';
import { MusclePicker } from '@/components/MusclePicker';
import { FormField } from '@/components/ui';
import { Button } from '@/components/ui/button';
import { Checkbox } from '@/components/ui/checkbox';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { NativeSelect, NativeSelectOption } from '@/components/ui/native-select';
import { Textarea } from '@/components/ui/textarea';
import { useLoad } from '@/lib/hooks';
import { LEVELS, MAX_MUSCLES, MUSCLE_GROUPS, validateLibraryExercise, type LibraryErrors } from '@/lib/logic';
import { loadCatalogue } from '@/lib/muscles';
import type { LibraryFields, LibraryInput } from '@/lib/types';

const BLANK: LibraryInput = {
  name: '', muscleGroup: 'Chest', equipment: '', level: 'Beginner', instructions: '', tips: '',
  defaultSets: 3, defaultReps: 10, defaultWeightKg: 0, videoUrl: '', active: true, primaryMuscles: [], secondaryMuscles: [],
};

export interface ExerciseFormProps {
  /** The exercise being edited; null for a new one. */
  initial: LibraryInput | null;
  /** Called with the checked values. Resolves to an error for the Name field, or nothing when saved. */
  onSave: (value: LibraryFields) => Promise<string | undefined>;
  onCancel: () => void;
}

export function ExerciseForm({ initial, onSave, onCancel }: ExerciseFormProps) {
  const [values, setValues] = useState<LibraryInput>(initial ?? BLANK);
  const [errors, setErrors] = useState<LibraryErrors>({});
  const [busy, setBusy] = useState(false);
  // The muscles to choose from; the form still works when they cannot be loaded (nothing can be added then).
  const muscles = useLoad(loadCatalogue).data?.groups;
  const set = <K extends keyof LibraryInput>(key: K, value: LibraryInput[K]) => setValues((current) => ({ ...current, [key]: value }));
  const text = (key: 'name' | 'equipment' | 'videoUrl') => ({ value: values[key], onChange: (event: { target: { value: string } }) => set(key, event.target.value) });
  const number = (key: 'defaultSets' | 'defaultReps' | 'defaultWeightKg') => ({ value: values[key], onChange: (event: { target: { value: string } }) => set(key, event.target.value) });

  async function submit(event: FormEvent) {
    event.preventDefault();
    const result = validateLibraryExercise(values);
    if (!result.ok) { setErrors(result.errors); return; }
    setBusy(true);
    const problem = await onSave(result.value);
    setBusy(false);
    setErrors(problem ? { name: problem } : {});
  }

  const groups = [...new Set([...MUSCLE_GROUPS, values.muscleGroup])];
  return (
    <form className="grid gap-4" noValidate onSubmit={(event) => void submit(event)}>
      <FormField label="Name" error={errors.name}>
        {(id) => <Input id={id} type="text" name="name" maxLength={100} aria-invalid={Boolean(errors.name)} {...text('name')} />}
      </FormField>
      <div className="grid gap-4 sm:grid-cols-2">
        <FormField label="Muscle group" error={errors.muscleGroup}>
          {(id) => (
            <NativeSelect id={id} className="w-full" name="muscleGroup" value={values.muscleGroup} onChange={(event) => set('muscleGroup', event.target.value)}>
              {groups.map((group) => <NativeSelectOption key={group} value={group}>{group}</NativeSelectOption>)}
            </NativeSelect>
          )}
        </FormField>
        <FormField label="Level" error={errors.level}>
          {(id) => (
            <NativeSelect id={id} className="w-full" name="level" value={values.level} onChange={(event) => set('level', event.target.value)}>
              {LEVELS.map((level) => <NativeSelectOption key={level} value={level}>{level}</NativeSelectOption>)}
            </NativeSelect>
          )}
        </FormField>
      </div>
      <FormField label="Main muscles" error={errors.primaryMuscles} hint="The muscles this exercise trains most. They are what the 3D body lists for a muscle.">
        {() => (
          <MusclePicker label="main muscles" value={values.primaryMuscles ?? []} onChange={(next) => set('primaryMuscles', next)} groups={muscles} exclude={values.secondaryMuscles ?? []} max={MAX_MUSCLES} />
        )}
      </FormField>
      <FormField label="Also works" error={errors.secondaryMuscles} hint="Muscles that help. Optional.">
        {() => (
          <MusclePicker label="helping muscles" value={values.secondaryMuscles ?? []} onChange={(next) => set('secondaryMuscles', next)} groups={muscles} exclude={values.primaryMuscles ?? []} max={MAX_MUSCLES} />
        )}
      </FormField>
      <FormField label="Equipment" error={errors.equipment} hint="For example Barbell, Dumbbells, Machine, Bodyweight">
        {(id) => <Input id={id} type="text" name="equipment" maxLength={40} {...text('equipment')} />}
      </FormField>
      <FormField label="How to do it" error={errors.instructions}>
        {(id) => <Textarea id={id} name="instructions" rows={4} maxLength={4000} value={values.instructions} onChange={(event) => set('instructions', event.target.value)} />}
      </FormField>
      <FormField label="Tip" error={errors.tips}>
        {(id) => <Textarea id={id} name="tips" rows={2} maxLength={1000} value={values.tips} onChange={(event) => set('tips', event.target.value)} />}
      </FormField>
      <div className="grid gap-4 sm:grid-cols-3">
        <FormField label="Sets" error={errors.defaultSets}>
          {(id) => <Input id={id} type="number" name="defaultSets" min={1} max={20} step={1} {...number('defaultSets')} />}
        </FormField>
        <FormField label="Reps" error={errors.defaultReps}>
          {(id) => <Input id={id} type="number" name="defaultReps" min={1} max={100} step={1} {...number('defaultReps')} />}
        </FormField>
        <FormField label="Weight (kg)" error={errors.defaultWeightKg}>
          {(id) => <Input id={id} type="number" name="defaultWeightKg" min={0} max={1000} step={0.5} {...number('defaultWeightKg')} />}
        </FormField>
      </div>
      <FormField label="Video link" error={errors.videoUrl} hint="Optional. Must start with https://">
        {(id) => <Input id={id} type="url" name="videoUrl" placeholder="https://…" {...text('videoUrl')} />}
      </FormField>
      <Label className="flex items-center gap-2">
        <Checkbox name="active" checked={values.active} onCheckedChange={(checked) => set('active', checked)} /> Show this exercise to users
      </Label>
      <div className="flex justify-end gap-2">
        <Button variant="outline" type="button" onClick={onCancel}>Cancel</Button>
        <Button type="submit" disabled={busy}>{initial?.name ? 'Save changes' : 'Add to library'}</Button>
      </div>
    </form>
  );
}
